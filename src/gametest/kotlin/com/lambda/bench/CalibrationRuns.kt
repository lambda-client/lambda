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
import com.lambda.threading.runSafe
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.math.Vec3d
import java.io.File
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.max

/**
 * WP0.6 calibration pass (docs/benchmark-harness-spec.md §6): scripted
 * max-effort runs per movement mode, driven through real key input on
 * purpose-built tracks. Measures the movement-rate caps the T1 anisotropic
 * heuristic and the F1 cost calibration consume — the theory notes' nominal
 * b/t table replaced by numbers from this client on this Minecraft version.
 *
 * Runs inside the scenario suite's world ([PathfinderScenarioTest]) — no
 * second client world. Output: `calibration.json` (rates + provenance) and
 * one telemetry JSONL per run, in the shared benchmark run directory.
 */
object CalibrationRuns {
    /** Sliding-window width for sustained-rate estimates, in ticks. */
    private const val RATE_WINDOW_TICKS = 20

    private data class CalibrationRun(
        val name: String,
        /** Which theory-note constant this measures. */
        val purpose: String,
        val fixture: List<String>,
        /** Teleport target (yaw is always -90: facing +x). */
        val startX: Double,
        val startY: Double,
        val startZ: Double,
        val ticks: Int,
        val holdForward: Boolean = true,
        val holdSprint: Boolean = false,
        val holdJump: Boolean = false,
        /** Stop sampling early once the player lands (fall runs). */
        val stopOnLanding: Boolean = false,
    )

    private data class TickSample(val x: Double, val y: Double, val z: Double, val onGround: Boolean)

    private data class RunResult(
        val run: CalibrationRun,
        val sustainedHorizontalBpt: Double,
        val sustainedAscentBpt: Double,
        val maxDescentBpt: Double,
        val sampledTicks: Int,
    )

    // Every fixture lives in the scenario region; same reset discipline as
    // TraversalScenarios. The fall column is cleared far above it.
    private val RESET = listOf(
        "/fill -16 62 -14 24 78 14 minecraft:air",
        "/fill -16 63 -14 24 63 14 minecraft:stone",
    )

    private val runs = listOf(
        CalibrationRun(
            name = "walk-flat",
            purpose = "T1 baseline: nominal 0.216 b/t",
            fixture = emptyList(),
            startX = -13.5, startY = 64.0, startZ = 0.5,
            ticks = 60,
        ),
        CalibrationRun(
            name = "sprint-flat",
            purpose = "T1 v_h candidate: nominal 0.281 b/t",
            fixture = emptyList(),
            startX = -13.5, startY = 64.0, startZ = 0.5,
            ticks = 60,
            holdSprint = true,
        ),
        CalibrationRun(
            name = "sprint-jump-flat",
            purpose = "T1 v_h true cap once jump maneuvers exist: nominal ~0.356 b/t; " +
                "the constant that makes today's Euclidean/sprint heuristic inadmissible",
            fixture = emptyList(),
            startX = -13.5, startY = 64.0, startZ = 0.5,
            ticks = 80,
            holdSprint = true,
            holdJump = true,
        ),
        CalibrationRun(
            name = "ascend-stairs",
            purpose = "T1 r_up: sustained repeated 1-block jump-ups, nominal 0.10-0.14 b/t",
            fixture = buildList {
                for (i in 0..7) add("/fill ${2 * i} ${64 + i} -2 ${2 * i + 1} ${64 + i} 2 minecraft:stone")
                add("/fill 16 64 -2 17 74 2 minecraft:stone") // end wall: stops the run on top
            },
            startX = -2.5, startY = 64.0, startZ = 0.5,
            ticks = 100,
            holdJump = true,
        ),
        CalibrationRun(
            name = "fall-free",
            purpose = "T1 r_down: free fall over ~120 blocks, asymptote 3.92 b/t",
            fixture = listOf("/fill -1 62 -1 1 190 1 minecraft:air"),
            startX = 0.5, startY = 185.0, startZ = 0.5,
            ticks = 120,
            holdForward = false,
            stopOnLanding = true,
        ),
    )

    /** Runs the whole pass in the caller's world; throws on a broken pipeline. */
    fun run(context: ClientGameTestContext, server: TestServerContext) {
        val results = mutableListOf<RunResult>()

        for (run in runs) {
            LOG.info("[Calibration] Running '${run.name}' — ${run.purpose}")
            val result = context.measure(run, server)
            LOG.info(
                "[Calibration] ${run.name}: horizontal=%.4f b/t ascent=%.4f b/t maxDescent=%.4f b/t (%d ticks)"
                    .format(
                        result.sustainedHorizontalBpt,
                        result.sustainedAscentBpt,
                        result.maxDescentBpt,
                        result.sampledTicks,
                    )
            )
            results += result
        }

        writeReport(results)
        LOG.info("[Calibration] Report written to ${ScenarioRunner.outputDir.absolutePath}/calibration.json")

        // Sanity gates only — loose bounds that catch a broken measurement
        // pipeline (keys not held, teleport failed), never a tuning drift.
        val sprint = results.first { it.run.name == "sprint-flat" }
        val sprintJump = results.first { it.run.name == "sprint-jump-flat" }
        check(sprint.sustainedHorizontalBpt > 0.2) {
            "sprint-flat measured ${sprint.sustainedHorizontalBpt} b/t — input injection likely broken"
        }
        check(sprintJump.sustainedHorizontalBpt > sprint.sustainedHorizontalBpt) {
            "sprint-jump (${sprintJump.sustainedHorizontalBpt}) not faster than sprint " +
                "(${sprint.sustainedHorizontalBpt}) — jump key likely not held"
        }
    }

    private fun ClientGameTestContext.measure(run: CalibrationRun, server: TestServerContext): RunResult {
        (RESET + run.fixture).forEach(server::runCommand)
        server.runCommand("/tp Steve ${run.startX} ${run.startY} ${run.startZ} -90 0")
        waitTicks(10)

        val samples = ArrayList<TickSample>(run.ticks)
        val telemetry = StringBuilder()

        if (run.holdForward) input.holdKey { it.forwardKey }
        if (run.holdSprint) input.holdKey { it.sprintKey }
        if (run.holdJump) input.holdKey { it.jumpKey }
        try {
            for (tick in 1..run.ticks) {
                waitTick()
                val sample = computeOnClient<TickSample, IllegalStateException> {
                    runSafe {
                        TickSample(player.pos.x, player.pos.y, player.pos.z, player.isOnGround)
                    } ?: TickSample(Double.NaN, Double.NaN, Double.NaN, false)
                }
                samples += sample
                telemetry.appendLine(
                    "{\"t\":$tick,\"x\":%.4f,\"y\":%.4f,\"z\":%.4f,\"ground\":%b}"
                        .format(sample.x, sample.y, sample.z, sample.onGround)
                )
                // Landing detection needs a few airborne ticks first so the
                // post-teleport settle does not stop the run immediately.
                if (run.stopOnLanding && tick > 5 && sample.onGround) break
            }
        } finally {
            if (run.holdForward) input.releaseKey { it.forwardKey }
            if (run.holdSprint) input.releaseKey { it.sprintKey }
            if (run.holdJump) input.releaseKey { it.jumpKey }
        }

        File(ScenarioRunner.outputDir, "calibration-${run.name}.jsonl").writeText(telemetry.toString())
        return RunResult(
            run = run,
            sustainedHorizontalBpt = maxWindowRate(samples) { a, b -> hypot(b.x - a.x, b.z - a.z) },
            sustainedAscentBpt = maxWindowRate(samples) { a, b -> b.y - a.y },
            maxDescentBpt = samples.zipWithNext().maxOfOrNull { (a, b) -> a.y - b.y } ?: 0.0,
            sampledTicks = samples.size,
        )
    }

    /**
     * Best sustained rate: max over sliding [RATE_WINDOW_TICKS] windows of
     * displacement/window — insensitive to the acceleration phase at the
     * start and any wall stop at the end.
     */
    private inline fun maxWindowRate(
        samples: List<TickSample>,
        displacement: (TickSample, TickSample) -> Double,
    ): Double {
        if (samples.size <= RATE_WINDOW_TICKS) return 0.0
        var best = 0.0
        for (i in 0 until samples.size - RATE_WINDOW_TICKS) {
            best = max(best, displacement(samples[i], samples[i + RATE_WINDOW_TICKS]) / RATE_WINDOW_TICKS)
        }
        return best
    }

    private fun writeReport(results: List<RunResult>) {
        val minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        File(ScenarioRunner.outputDir, "calibration.json").writeText(
            buildString {
                appendLine("{")
                appendLine("  \"date\": \"${LocalDate.now()}\",")
                appendLine("  \"minecraft\": \"$minecraftVersion\",")
                appendLine("  \"rateWindowTicks\": $RATE_WINDOW_TICKS,")
                appendLine("  \"runs\": [")
                results.forEachIndexed { index, result ->
                    append("    {")
                    append("\"name\":\"${result.run.name}\"")
                    append(",\"purpose\":\"${result.run.purpose}\"")
                    append(",\"sustainedHorizontalBpt\":%.4f".format(result.sustainedHorizontalBpt))
                    append(",\"sustainedAscentBpt\":%.4f".format(result.sustainedAscentBpt))
                    append(",\"maxDescentBpt\":%.4f".format(result.maxDescentBpt))
                    append(",\"sampledTicks\":${result.sampledTicks}")
                    append("}")
                    appendLine(if (index < results.lastIndex) "," else "")
                }
                appendLine("  ]")
                appendLine("}")
            }
        )
    }
}
