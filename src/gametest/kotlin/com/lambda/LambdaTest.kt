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
import com.lambda.PathingTestHarness.scenarioFilter
import com.lambda.PathingTestHarness.unit
import com.lambda.util.combat.DamageUtils.isFallDeadly
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screen.world.WorldCreator

@Suppress("UnstableApiUsage")
object LambdaTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        // Every planner refusal in a gametest becomes a replayable JVM fixture.
        System.setProperty("lambda.pathing.dumpFailures", "true")
        val singleplayerContext = context.worldBuilder()
            .adjustSettings {
                it.gameMode = WorldCreator.Mode.CREATIVE
            }
            .create()

        val world = singleplayerContext.clientWorld
        val server = singleplayerContext.server

        world.waitForChunksDownload()

        // The fall checks are unrelated to pathing and cost a world round trip each, so a
        // filtered run -- which is what working on the planner actually needs -- skips
        // straight to the scenarios.
        if (scenarioFilter.isEmpty()) {
            server.runCommand("/tp Steve ~ ~30 ~")
            context.unit("assert deadly fall") { isFallDeadly() }

            server.runCommand("/tp Steve ~ -50 ~")
            context.unit("assert safe fall") { !isFallDeadly() }
        }
        server.runCommand("/tp Steve ~ -60 ~")

        MovementReplayTests.run(context, server)

        // The pathing scenarios below are the planner's own tapes, walked live by
        // PathingManager -- the same path a `.path` command takes. A simulator-vs-
        // simulator unit test cannot see vanilla divergence, so every coarse move
        // kind M3 claims must land here.
        PathingMetricSink.reset()
        restoreArena(server)

        WalkPathingTests.run(context, server)
        CoarsePathingTests.run(context, server)
        TrajectoryPathingTests.run(context, server)

        PathingTestHarness.measurementReport.forEach { println("[measure] $it") }

        check(pathingFailures.isEmpty()) {
            "${pathingFailures.size} pathing scenario(s) failed:\n" + pathingFailures.joinToString("\n")
        }

        // All the tests passed
        singleplayerContext.close()
    }
}
