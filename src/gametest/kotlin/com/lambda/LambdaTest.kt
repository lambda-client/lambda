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
import com.lambda.pathing.goal.TraversalGoal
import com.lambda.pathing.manager.PathfinderExecutor
import com.lambda.pathing.manager.PathfinderManager
import com.lambda.pathing.manager.TraversalHandle
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.world.fastVectorOf
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot

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

        try {
            world.waitForChunksDownload()

            server.runCommand("/tp Steve ~ ~30 ~")
            context.unit("assert deadly fall") { isFallDeadly() }

            server.runCommand("/tp Steve ~ -50 ~")
            context.unit("assert safe fall") { !isFallDeadly() }
            server.runCommand("/tp Steve ~ -60 ~")

            context.pathfinderFlatWalkSmoke(server)
        } finally {
            context.runOnClient<IllegalStateException> {
                PathfinderManager.cancelActiveTraversal()
            }
            singleplayerContext.close()
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

    private fun ClientGameTestContext.pathfinderFlatWalkSmoke(server: TestServerContext) {
        val start = Vec3d(0.5, 64.0, 0.5)
        val goal = fastVectorOf(8, 64, 0)

        server.runCommand("/fill -4 63 -4 12 63 4 minecraft:grass_block")
        server.runCommand("/fill -4 64 -4 12 68 4 minecraft:air")
        server.runCommand("/tp Steve ${start.x} ${start.y} ${start.z} 0 0")
        waitTicks(5)

        lateinit var handle: TraversalHandle
        runOnClient<IllegalStateException> {
            handle = with(PathfinderManager) {
                runSafeAutomated {
                    requestTraversal(
                        goal = TraversalGoal.Block(goal),
                        owner = this@LambdaTest,
                    )
                }
            } ?: throw IllegalStateException("Could not request pathfinder traversal in a safe automated context")

        }

        waitForSafe("async pathfinder publishes ready path", timeoutTicks = 80) {
            handle.status == TraversalHandle.Status.Ready && handle.path.size >= 2
        }
        runOnClient<IllegalStateException> {
            check(handle.path.size >= 2) {
                "Expected path with at least two nodes: ${handle.debugString()}"
            }
        }

        waitForSafe("path executor starts following", timeoutTicks = 40) {
            PathfinderExecutor.state.status == "Following" &&
                PathfinderExecutor.state.segmentCount > 0
        }

        waitForSafe("path executor writes movement input", timeoutTicks = 20) {
            val state = PathfinderExecutor.state
            hypot(state.commandedForward, state.commandedStrafe) > 0.05
        }

        waitForSafe("player starts moving on path", timeoutTicks = 80) {
            horizontalDistance(player.pos, start) > 0.75
        }

        waitForSafe("player reaches path goal despite camera yaw perturbation", timeoutTicks = 240) {
            // Simulate the user moving the camera while the executor is driving.
            // The rotation manager splits movement yaw from camera yaw, so this
            // should not perturb path following.
            player.yaw = 180.0f
            horizontalDistance(player.pos, Vec3d(8.5, 64.0, 0.5)) <= 1.1
        }

        waitForSafe("pathfinder traversal completes", timeoutTicks = 20) {
            PathfinderManager.activeTraversal?.status == TraversalHandle.Status.Succeeded
        }
    }

    private inline fun ClientGameTestContext.waitForSafe(
        label: String,
        timeoutTicks: Int,
        crossinline predicate: SafeContext.() -> Boolean,
    ) {
        repeat(timeoutTicks) {
            waitTick()
            val passed = computeOnClient<Boolean, IllegalStateException> {
                runSafe(predicate) ?: false
            }
            if (passed) return
        }

        val debug = computeOnClient<String, IllegalStateException> {
            buildString {
                appendLine("Pathfinder: ${PathfinderManager.debugInfo()}")
                appendLine("Executor: ${PathfinderExecutor.state}")
            }
        }
        throw IllegalStateException("Timed out waiting for $label after $timeoutTicks ticks.\n$debug")
    }

    private fun horizontalDistance(a: Vec3d, b: Vec3d): Double = hypot(a.x - b.x, a.z - b.z)
}
