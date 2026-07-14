/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.input.Input
import net.minecraft.client.gui.screen.world.WorldCreator
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

@Suppress("UnstableApiUsage")
object LambdaTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val singleplayerContext = context.worldBuilder()
            .adjustSettings {
                it.gameMode = WorldCreator.Mode.CREATIVE
            }
            .create()

        val world = singleplayerContext.clientWorld
        val server = singleplayerContext.server

        world.waitForChunksDownload()

        server.runCommand("/tp Steve ~ ~30 ~")
        context.unit("assert deadly fall") { isFallDeadly() }

        server.runCommand("/tp Steve ~ -50 ~")
        context.unit("assert safe fall") { !isFallDeadly() }
        server.runCommand("/tp Steve ~ -60 ~")

        testOrdinaryMovementReplay(context, server)

        // All the tests passed
        singleplayerContext.close()
    }

    private fun testOrdinaryMovementReplay(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
    ) {
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }

        assertMovementReplay(context, "walk-jump", buildList {
            repeat(4) { add(MovementSimulationInput(forward = 1.0)) }
            add(MovementSimulationInput(forward = 1.0, jump = true))
            repeat(6) { add(MovementSimulationInput(forward = 1.0)) }
        })

        server.runCommand("/setblock 0 100 2 minecraft:stone_slab[type=bottom]")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(
            context = context,
            scenario = "slab-auto-step",
            tape = List(12) { MovementSimulationInput(forward = 1.0) },
        )

        server.runCommand("/setblock 0 100 2 minecraft:air")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "sprint-coast", buildList {
            repeat(7) { add(MovementSimulationInput(forward = 1.0, sprint = true)) }
            repeat(7) { add(MovementSimulationInput()) }
        })

        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "steered-turn", buildList {
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(45.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(90.0, 0.0))) }
        })

        server.runCommand("/setblock 0 100 2 minecraft:stone")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(
            context = context,
            scenario = "full-block-wall",
            tape = List(12) { MovementSimulationInput(forward = 1.0) },
        )
    }

    private fun assertMovementReplay(
        context: ClientGameTestContext,
        scenario: String,
        tape: List<MovementSimulationInput>,
    ) {
        lateinit var previousInput: Input
        lateinit var expected: List<MovementSimulationState>

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "Movement replay player did not settle on the test platform" }

            previousInput = player.input
            val initial = MovementSimulationState.from(player)
            val profile = PlayerPhysicsProfile.capture(player)
            val center = player.blockPos
            val snapshot = SnapshotSimulationEnvironment.capture(
                world = player.entityWorld,
                player = player,
                bounds = SimulationSnapshotBounds(
                    minX = center.x - 6,
                    minY = center.y - 5,
                    minZ = center.z - 6,
                    maxX = center.x + 6,
                    maxY = center.y + 6,
                    maxZ = center.z + 10,
                ),
            )

            val liveSimulator = MovementSimulator(player, initial)
            val liveExpected = tape.map { liveSimulator.tickMovement(it).simulator.state }
            expected = CompletableFuture.supplyAsync {
                val snapshotSimulator = MovementSimulator(profile, snapshot, initial)
                tape.map { snapshotSimulator.tickMovement(it).simulator.state }
            }.join()

            liveExpected.zip(expected).forEachIndexed { frame, (live, captured) ->
                assertNear(live.position.x, captured.position.x, "$scenario snapshot frame $frame position.x")
                assertNear(live.position.y, captured.position.y, "$scenario snapshot frame $frame position.y")
                assertNear(live.position.z, captured.position.z, "$scenario snapshot frame $frame position.z")
                assertNear(live.velocity.x, captured.velocity.x, "$scenario snapshot frame $frame velocity.x")
                assertNear(live.velocity.y, captured.velocity.y, "$scenario snapshot frame $frame velocity.y")
                assertNear(live.velocity.z, captured.velocity.z, "$scenario snapshot frame $frame velocity.z")
                check(live.onGround == captured.onGround) {
                    "$scenario snapshot frame $frame onGround differs"
                }
            }
        }

        tape.forEachIndexed { frame, input ->
            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                input.rotation?.let { rotation ->
                    player.yaw = rotation.yawF
                    player.pitch = rotation.pitchF
                }
                player.input = buildMovementInput(
                    forward = input.forward,
                    strafe = input.strafe,
                    jump = input.jump,
                    sneak = input.sneak,
                    sprint = input.sprint,
                )
            }

            context.waitTick()

            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                val predicted = expected[frame]
                assertNear(predicted.position.x, player.x, "$scenario frame $frame position.x")
                assertNear(predicted.position.y, player.y, "$scenario frame $frame position.y")
                assertNear(predicted.position.z, player.z, "$scenario frame $frame position.z")
                assertNear(predicted.velocity.x, player.velocity.x, "$scenario frame $frame velocity.x")
                assertNear(predicted.velocity.y, player.velocity.y, "$scenario frame $frame velocity.y")
                assertNear(predicted.velocity.z, player.velocity.z, "$scenario frame $frame velocity.z")
                check(predicted.onGround == player.isOnGround) {
                    "$scenario frame $frame onGround: expected ${predicted.onGround}, actual ${player.isOnGround}"
                }
            }
        }

        context.runOnClient<IllegalStateException> {
            Lambda.mc.player?.input = previousInput
        }
    }

    private fun assertNear(expected: Double, actual: Double, label: String) {
        check(abs(expected - actual) <= MOVEMENT_EPSILON) {
            "$label: expected $expected, actual $actual"
        }
    }

    inline fun ClientGameTestContext.unit(label: String, crossinline block: SafeContext.() -> Boolean) {
        waitTick()

        runOnClient<IllegalStateException> {
            val asserted = runSafe(block)
                ?: throw IllegalStateException("Could not run in a safe context")

            check(asserted) { "Assertion failed: $label" }
        }
    }

    private const val MOVEMENT_EPSILON = 1.0E-6
}
