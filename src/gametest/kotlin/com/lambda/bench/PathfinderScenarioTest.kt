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

package com.lambda.bench

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.bench.ScenarioRunner.runScenario
import com.lambda.pathing.manager.PathfinderManager
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screen.world.WorldCreator

/**
 * Pathfinder traversal scenario suite (WP0.1).
 *
 * Runs every [TraversalScenarios] case in one flat creative world, writes
 * per-tick telemetry + summary.json to logs/benchmarks/<run-id>/ in the game
 * directory, and fails the gametest if any scenario misses its expected
 * outcome. The telemetry files are the primary deliverable — they are the
 * data source for jump-timing analysis and the calibration pass.
 */
@Suppress("UnstableApiUsage")
object PathfinderScenarioTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val singleplayerContext = context.worldBuilder()
            .adjustSettings {
                it.gameMode = WorldCreator.Mode.CREATIVE
            }
            .create()

        val server = singleplayerContext.server
        val reports = mutableListOf<ScenarioReport>()

        try {
            singleplayerContext.clientWorld.waitForChunksDownload()

            // Auto-jump silently climbs step-ups without any executor jump
            // input, masking the exact behavior these scenarios measure.
            context.runOnClient<IllegalStateException> {
                mc.options.autoJump.value = false
            }

            // Deterministic environment for every scenario.
            // Game-rule ids were snake-cased/renamed in 1.21.11. The old
            // camelCase commands fail without throwing, silently making the
            // supposedly deterministic harness nondeterministic.
            server.runCommand("/gamerule advance_time false")
            server.runCommand("/gamerule advance_weather false")
            server.runCommand("/gamerule spawn_mobs false")
            server.runCommand("/gamerule random_tick_speed 0")
            server.runCommand("/time set noon")

            // -Pbench.filter=gap,stair → run matching scenarios only.
            val filter = System.getProperty("lambda.bench.filter")
                .orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            val selected = if (filter.isEmpty()) TraversalScenarios.all
            else TraversalScenarios.all.filter { s -> filter.any(s.name::contains) }

            for (scenario in selected) {
                LOG.info("[Bench] Running scenario '${scenario.name}' — ${scenario.purpose}")
                val report = context.runScenario(scenario, server)
                LOG.info("[Bench] ${report.summaryLine()}")
                reports += report
            }

            // WP0.6 calibration (same world/session): opt-in — the constants
            // are stable measurements, only worth re-running after an MC
            // version bump or a movement-simulator change.
            if (System.getProperty("lambda.bench.calibrate").toBoolean()) {
                CalibrationRuns.run(context, server)
            } else {
                LOG.info("[Bench] Calibration skipped (-Pbench.calibrate=true to run)")
            }
        } finally {
            context.runOnClient<IllegalStateException> {
                PathfinderManager.cancelActiveTraversal()
            }
            ScenarioRunner.writeSummary(reports)
            LOG.info("[Bench] Telemetry written to ${ScenarioRunner.outputDir.absolutePath}")
            singleplayerContext.close()
        }

        // Ungated (H6 baseline) misses are the measurement, not a regression:
        // log them loudly, fail the suite only on gated scenarios.
        reports.filter { !it.gated && !it.passed }.forEach {
            LOG.warn("[Bench] baseline miss (ungated): ${it.summaryLine()}")
        }

        // Ungated scenarios may miss because they measure an executor
        // baseline. A planner that never produced a path is infrastructure,
        // not a baseline result, and must always fail the suite.
        val failed = reports.filter { (!it.passed && it.gated) || it.plannerStalled }
        check(failed.isEmpty()) {
            buildString {
                appendLine("${failed.size}/${reports.size} scenarios failed:")
                failed.forEach { appendLine("  ${it.summaryLine()}") }
                appendLine("Telemetry: ${ScenarioRunner.outputDir.absolutePath}")
            }
        }
    }
}
