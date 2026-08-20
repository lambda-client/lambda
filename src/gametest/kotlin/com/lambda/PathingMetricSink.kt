/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Minimal Phase 0 JSONL sink; the full comparative harness belongs to Phase 6. */
object PathingMetricSink {
    data class Run(
        val scenario: String,
        val success: Boolean,
        val completionTicks: Int,
        val trajectoryFrames: Int,
        val collisionFrames: Int,
        val bumps: Int,
        val launchMarginFrames: Int,
        val planLatencyMs: Long,
        val maxReplayDeviation: Double,
        /** Frames of the final published tape; informational, never gated. */
        val finalFrames: Int = 0,
    )

    private val output: Path = Path.of(
        System.getProperty("lambda.pathing.metrics", "build/reports/pathing/pathing-metrics.jsonl"),
    )
    private val baseline: Map<String, Run> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/pathing-metrics-baseline.jsonl")) {
            "Missing checked-in pathing metrics baseline"
        }
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map(::parse).associateBy(Run::scenario)
        }
    }

    fun reset() {
        output.parent?.let(Files::createDirectories)
        Files.writeString(
            output,
            "",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    fun record(run: Run) {
        output.parent?.let(Files::createDirectories)
        Files.writeString(
            output,
            run.toJsonLine() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /**
     * Run-to-run spread of a horizon that commits against a live walk.
     *
     * Narrow, because the horizon is reproducible again. It was not: commitment quality
     * used to depend on how much the search happened to explore before the body forced the
     * decision, so the same build produced 188 to 238 frames on `pathing-bedrock-field`
     * across consecutive runs. Requiring a minimum exploration behind every commitment
     * (`minCommitExpansions`) removed the spread entirely -- three consecutive runs now
     * agree to the frame -- and this is back to covering ordinary jitter rather than
     * hiding a planner that cannot repeat itself.
     */
    private const val TRAJECTORY_FRAME_SLACK = 3
    private const val TRAJECTORY_FRAME_SLACK_RATIO = 0.03

    /** Small deterministic regression gate; Phase 6 grows this into comparative statistics. */
    fun assertWithinBaseline(run: Run) {
        check(run.success) { "${run.scenario}: run did not complete" }
        if (REBASELINE) {
            // Collecting a fresh baseline (a deliberate metric-semantics change): the
            // run must still complete, but the numeric gates are the thing being reset.
            return
        }
        val expected = baseline[run.scenario] ?: run {
            // A scenario's first-ever run has nothing to regress against: record it,
            // then check the emitted line into the baseline to arm the gate.
            System.err.println("[PathingMetricSink] no baseline for ${run.scenario}; recorded, not gated")
            return
        }
        val completionAllowance = expected.completionTicks + maxOf(
            COMPLETION_TICK_SLACK,
            (expected.completionTicks * COMPLETION_TICK_SLACK_RATIO).toInt(),
        )
        check(run.completionTicks <= completionAllowance) {
            "${run.scenario}: completion regressed ${expected.completionTicks} -> ${run.completionTicks} ticks"
        }
        // The receding horizon commits when the body is about to run out of committed
        // motion, so *when* each commitment happens depends on how fast the machine ran
        // that second, and the tape it produces is genuinely not reproducible. There is no
        // longer a deterministic first plan to gate on instead -- the whole walk is the
        // plan. So the gate bounds the variance rather than pretending it is absent; a
        // real regression moves this much further than a few frames.
        val allowance = expected.trajectoryFrames +
            maxOf(TRAJECTORY_FRAME_SLACK, (expected.trajectoryFrames * TRAJECTORY_FRAME_SLACK_RATIO).toInt())
        check(run.trajectoryFrames <= allowance) {
            "${run.scenario}: certified tape regressed ${expected.trajectoryFrames} -> ${run.trajectoryFrames} frames"
        }
        check(run.collisionFrames <= expected.collisionFrames) {
            "${run.scenario}: collision frames regressed ${expected.collisionFrames} -> ${run.collisionFrames}"
        }
        check(run.bumps <= expected.bumps) {
            "${run.scenario}: bumps regressed ${expected.bumps} -> ${run.bumps}"
        }
        check(run.launchMarginFrames >= expected.launchMarginFrames) {
            "${run.scenario}: launch margin regressed ${expected.launchMarginFrames} -> ${run.launchMarginFrames}"
        }
        check(run.planLatencyMs <= maxOf(MAX_PLAN_LATENCY_MS, expected.planLatencyMs * 3)) {
            "${run.scenario}: plan latency regressed ${expected.planLatencyMs} -> ${run.planLatencyMs} ms"
        }
        val deviationLimit = maxOf(
            expected.maxReplayDeviation * 1.5 + 1.0E-7,
            if (run.scenario == "pathing-long-haul") 1.7E-5 else 2.0E-6,
        )
        check(run.maxReplayDeviation <= deviationLimit) {
            "${run.scenario}: replay deviation ${run.maxReplayDeviation} exceeded $deviationLimit"
        }
    }

    private fun Run.toJsonLine(): String = buildString {
        append('{')
        append("\"scenario\":\"").append(scenario.replace("\\", "\\\\").replace("\"", "\\\""))
            .append("\",")
        append("\"success\":").append(success).append(',')
        append("\"completionTicks\":").append(completionTicks).append(',')
        append("\"trajectoryFrames\":").append(trajectoryFrames).append(',')
        append("\"finalFrames\":").append(finalFrames).append(',')
        append("\"collisionFrames\":").append(collisionFrames).append(',')
        append("\"bumps\":").append(bumps).append(',')
        append("\"launchMarginFrames\":").append(launchMarginFrames).append(',')
        append("\"planLatencyMs\":").append(planLatencyMs).append(',')
        append("\"maxReplayDeviation\":").append(maxReplayDeviation)
        append('}')
    }

    private fun parse(line: String): Run {
        fun string(name: String): String = checkNotNull(
            Regex("\\\"$name\\\":\\\"([^\\\"]*)\\\"").find(line)?.groupValues?.get(1),
        ) { "Missing $name in metrics baseline: $line" }
        fun number(name: String): String = checkNotNull(
            Regex("\\\"$name\\\":([^,}]+)").find(line)?.groupValues?.get(1),
        ) { "Missing $name in metrics baseline: $line" }
        return Run(
            scenario = string("scenario"),
            success = number("success").toBooleanStrict(),
            completionTicks = number("completionTicks").toInt(),
            trajectoryFrames = number("trajectoryFrames").toInt(),
            finalFrames = runCatching { number("finalFrames").toInt() }.getOrDefault(0),
            collisionFrames = number("collisionFrames").toInt(),
            bumps = number("bumps").toInt(),
            launchMarginFrames = number("launchMarginFrames").toInt(),
            planLatencyMs = number("planLatencyMs").toLong(),
            maxReplayDeviation = number("maxReplayDeviation").toDouble(),
        )
    }

    /**
     * Wall-clock slack on completion.
     *
     * Completion counts ticks from request to arrival, so it carries planning latency and,
     * under a receding horizon, the timing of every commitment along the way. On the
     * shortest scenarios that variance is a large fraction of a small number -- a 38-tick
     * walk came in at 54 -- so the allowance is proportional as well as absolute. It is
     * deliberately generous: this measures the client's wall clock, and what the planner
     * actually produced is gated tightly by `trajectoryFrames` and `collisionFrames`.
     */
    private const val COMPLETION_TICK_SLACK = 15
    private const val COMPLETION_TICK_SLACK_RATIO = 0.5
    private const val MAX_PLAN_LATENCY_MS = 1_500L

    /** `-Dlambda.pathing.rebaseline=true`: record metrics, skip the gates, for baseline refresh. */
    private val REBASELINE = System.getProperty("lambda.pathing.rebaseline").toBoolean()
}
