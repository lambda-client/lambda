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

import com.lambda.PathingTestHarness.pathingFailures
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.PathingTestHarness.scenarioSelected
import com.lambda.config.blocks.BreakConfig
import com.lambda.context.Automated
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.breaking.BreakRequest.Companion.breakRequest
import com.lambda.interaction.managers.interacting.InteractRequest.Companion.interactRequest
import com.lambda.threading.runSafeAutomated
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.ObserverBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("UnstableApiUsage")
internal object InteractionManagerTests {
    private val target = BlockPos(0, 100, 2)

    fun run(context: ClientGameTestContext, server: TestServerContext) {
        restoreArena(server)
        server.runCommand("/fill -4 99 -4 4 99 4 minecraft:stone")
        server.runCommand("/gamemode survival Steve")
        try {
            if (scenarioSelected("manager-break")) breakDirt(context, server)
            if (scenarioSelected("manager-break-early-stop")) stopEarly(context, server)
            if (scenarioSelected("manager-place")) placeStone(context, server)
            if (scenarioSelected("manager-place-facing")) placeObserverFacingAway(context, server)
            if (scenarioSelected("manager-door")) openDoor(context, server, "manager-door", listOf(target))
            if (scenarioSelected("manager-door-both-halves")) openDoor(context, server, "manager-door-both-halves", listOf(target, target.up()))
        } finally {
            server.runCommand("/clear Steve")
            server.runCommand("/gamemode creative Steve")
        }
    }

    private fun breakDirt(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("/setblock 0 100 2 minecraft:dirt")
        standFacingTarget(context, server)
        val pending = ConcurrentLinkedQueue<BuildContext>()
        drive(context, "manager-break", 100, { it.isAir }) {
            VanillaLikeBuild.runSafeAutomated {
                mapOf(target to TargetState.Empty).sim().breakRequest(pending)?.submit()
            }
        }
    }

    private object EarlyStopBuild : Automated by VanillaLikeBuild {
        override val breakConfig: BreakConfig = object : BreakConfig by VanillaLikeBuild.breakConfig {
            override val breakThreshold = 0.4F
        }
    }

    private fun stopEarly(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("/setblock 0 100 2 minecraft:dirt")
        standFacingTarget(context, server)
        val pending = ConcurrentLinkedQueue<BuildContext>()
        var ticks = 0
        while (ticks < 60 && !stateAt(context).isAir) {
            context.runOnClient<IllegalStateException> {
                EarlyStopBuild.runSafeAutomated {
                    mapOf(target to TargetState.Empty).sim().breakRequest(pending)?.submit()
                }
            }
            context.waitTick()
            ticks++
        }
        val failure = if (!stateAt(context).isAir) "no predicted break after $ticks ticks" else {
            val states = List(40) { context.waitTick(); stateAt(context) }
            val restored = states.indexOfFirst { !it.isAir }
            val rebroken = states.withIndex().indexOfFirst { (index, state) -> index > restored && state.isAir }
            when {
                restored !in 0..ACK_TICKS -> "the rejected STOP was not rolled back at the ack (restored at $restored)"
                rebroken < 0 || !states.last().isAir -> "the server never finished the rejected break"
                else -> null.also {
                    println("[pathing-diag] manager-break-early-stop: predicted after $ticks ticks, rolled back after ${restored + 1}, broken by the server after ${rebroken + 1}")
                }
            }
        }
        if (failure != null) {
            pathingFailures += "manager-break-early-stop: $failure"
            println("[pathing-fail] manager-break-early-stop: $failure")
        }
    }

    private fun placeStone(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("/setblock 0 100 2 minecraft:air")
        server.runCommand("/give Steve minecraft:stone 8")
        standFacingTarget(context, server)
        val pending = ConcurrentLinkedQueue<BuildContext>()
        drive(context, "manager-place", 60, { it.isOf(Blocks.STONE) }) {
            VanillaLikeBuild.runSafeAutomated {
                mapOf(target to TargetState.State(Blocks.STONE.defaultState)).sim().interactRequest(pending)?.submit()
            }
        }
    }

    private fun placeObserverFacingAway(context: ClientGameTestContext, server: TestServerContext) {
        val pos = BlockPos(2, 100, 0)
        val expected = Blocks.OBSERVER.defaultState.with(ObserverBlock.FACING, Direction.EAST)
        server.runCommand("/setblock 2 100 0 minecraft:air")
        server.runCommand("/clear Steve")
        server.runCommand("/give Steve minecraft:observer 4")
        standFacingTarget(context, server)
        val pending = ConcurrentLinkedQueue<BuildContext>()
        var ticks = 0
        var predicted = stateAt(context, pos)
        while (ticks < 60 && predicted.isAir) {
            context.runOnClient<IllegalStateException> {
                VanillaLikeBuild.runSafeAutomated {
                    mapOf(pos to TargetState.State(expected)).sim().interactRequest(pending)?.submit()
                }
            }
            context.waitTick()
            ticks++
            predicted = stateAt(context, pos)
        }
        repeat(20) { context.waitTick() }
        val settled = stateAt(context, pos)
        val failure = when {
            predicted.isAir -> "nothing placed after $ticks ticks"
            settled != expected -> "settled as $settled instead of $expected"
            predicted != settled -> "predicted $predicted, the server placed $settled"
            else -> null
        }
        if (failure != null) {
            pathingFailures += "manager-place-facing: $failure"
            println("[pathing-fail] manager-place-facing: $failure")
        } else println("[pathing-diag] manager-place-facing: predicted the server's facing after $ticks ticks and stayed")
    }

    private fun openDoor(context: ClientGameTestContext, server: TestServerContext, scenario: String, halves: List<BlockPos>) {
        server.runCommand("/clear Steve")
        server.runCommand("/setblock 0 101 2 minecraft:air")
        server.runCommand("/setblock 0 100 2 minecraft:oak_door[half=lower,facing=north,open=false]")
        server.runCommand("/setblock 0 101 2 minecraft:oak_door[half=upper,facing=north,open=false]")
        standFacingTarget(context, server)
        val pending = ConcurrentLinkedQueue<BuildContext>()
        drive(context, scenario, 60, { it.isOf(Blocks.OAK_DOOR) && it.get(DoorBlock.OPEN) }) {
            VanillaLikeBuild.runSafeAutomated {
                halves.associateWith { TargetState.State(world.getBlockState(it).with(DoorBlock.OPEN, true)) }.sim().interactRequest(pending)?.submit()
            }
        }
    }

    private fun standFacingTarget(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("/tp Steve 0.5 100 0.5 0 30")
        repeat(10) { context.waitTick() }
    }

    private fun drive(context: ClientGameTestContext, scenario: String, maxTicks: Int, done: (BlockState) -> Boolean, submit: () -> Unit) {
        var ticks = 0
        while (ticks < maxTicks && !done(stateAt(context))) {
            context.runOnClient<IllegalStateException> { submit() }
            context.waitTick()
            ticks++
        }
        if (!done(stateAt(context))) {
            pathingFailures += "$scenario: not done after $maxTicks ticks, block is ${stateAt(context)}"
            println("[pathing-fail] $scenario: not done after $maxTicks ticks")
            return
        }
        repeat(20) { context.waitTick() }
        val settled = stateAt(context)
        if (!done(settled)) {
            pathingFailures += "$scenario: done after $ticks ticks, then rolled back to $settled"
            println("[pathing-fail] $scenario: rolled back to $settled")
            return
        }
        println("[pathing-diag] $scenario: done after $ticks ticks and stayed")
    }

    private const val ACK_TICKS = 3

    private fun stateAt(context: ClientGameTestContext, pos: BlockPos = target): BlockState =
        context.computeOnClient<BlockState, IllegalStateException> { client -> client.world!!.getBlockState(pos) }
}
