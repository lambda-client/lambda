/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Loopback-only bridge from Sample Factory Gymnasium workers to the exact JVM simulator. */
object RlBridgeServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options.parse(args)
        val environmentSpec = RlEnvironments.find(options.environment)
        val address = InetAddress.getByName(options.bind)
        val clients = AtomicInteger()
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "neolambda-rl-client-${clients.incrementAndGet()}").apply {
                isDaemon = true
            }
        }

        ServerSocket(options.port, 128, address).use { server ->
            server.reuseAddress = true
            println(
                "NeoLambda RL bridge ready on ${server.inetAddress.hostAddress}:${server.localPort} " +
                    "(environment=${environmentSpec.wireName}, protocol=${RlBridgeProtocol.VERSION}, " +
                    "obs=${environmentSpec.observationSize}, " +
                    "actions=${environmentSpec.actionDims.contentToString()})",
            )
            System.out.flush()
            while (!Thread.currentThread().isInterrupted) {
                val socket = server.accept()
                socket.tcpNoDelay = true
                executor.execute { serve(socket, environmentSpec) }
            }
        }
    }

    private fun serve(socket: Socket, environmentSpec: RlEnvironmentSpec) {
        socket.use {
            val input = DataInputStream(BufferedInputStream(it.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(it.getOutputStream()))
            try {
                require(input.readInt() == RlBridgeProtocol.MAGIC) { "Bad RL bridge magic" }
                require(input.readInt() == RlBridgeProtocol.VERSION) {
                    "Unsupported RL bridge version"
                }
                output.writeInt(RlBridgeProtocol.MAGIC)
                output.writeInt(RlBridgeProtocol.VERSION)
                output.writeInt(environmentSpec.observationSize)
                output.writeInt(environmentSpec.actionDims.size)
                environmentSpec.actionDims.forEach(output::writeInt)
                output.flush()

                val environment = environmentSpec.create()
                while (true) {
                    when (input.readUnsignedByte()) {
                        RlBridgeProtocol.RESET -> {
                            writeTransition(
                                output,
                                environment.reset(
                                    seed = input.readLong(),
                                    maxDifficulty = input.readUnsignedByte(),
                                ),
                            )
                        }

                        RlBridgeProtocol.STEP -> {
                            val action = IntArray(environmentSpec.actionDims.size) {
                                input.readInt()
                            }
                            writeTransition(output, environment.step(action))
                        }

                        RlBridgeProtocol.CLOSE -> return
                        else -> error("Unknown RL bridge opcode")
                    }
                    output.flush()
                }
            } catch (_: EOFException) {
                // Normal when a training worker is stopped or restarted.
            } catch (failure: Exception) {
                System.err.println("RL bridge client failed: ${failure.message}")
            }
        }
    }

    private fun writeTransition(
        output: DataOutputStream,
        transition: RlTransition,
    ) {
        output.writeFloat(transition.reward)
        output.writeBoolean(transition.terminated)
        output.writeBoolean(transition.truncated)
        output.writeByte(transition.outcome.wireId)
        output.writeInt(transition.ticks)
        output.writeInt(transition.scenario)
        output.writeFloat(transition.distanceToGoal)
        output.writeFloat(transition.density)
        transition.observation.forEach(output::writeFloat)
    }

    private data class Options(
        val bind: String,
        val port: Int,
        val environment: String,
    ) {
        companion object {
            fun parse(args: Array<String>): Options {
                var bind = "127.0.0.1"
                var port = 32121
                var environment = RlEnvironments.GAP_RUNNER.wireName
                var index = 0
                while (index < args.size) {
                    when (val arg = args[index++]) {
                        "--bind" -> bind = args.getOrNull(index++)
                            ?: error("--bind requires an address")
                        "--port" -> port = args.getOrNull(index++)?.toIntOrNull()
                            ?: error("--port requires an integer")
                        "--environment" -> environment = args.getOrNull(index++)
                            ?: error("--environment requires a name")
                        else -> error("Unknown argument: $arg")
                    }
                }
                require(port in 1..65535)
                return Options(bind, port, environment)
            }
        }
    }
}
