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
        val reroutes: Int,
        val maxReplayDeviation: Double,
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

    /** Small deterministic regression gate; Phase 6 grows this into comparative statistics. */
    fun assertWithinBaseline(run: Run) {
        val expected = checkNotNull(baseline[run.scenario]) { "No metrics baseline for ${run.scenario}" }
        check(run.success) { "${run.scenario}: run did not complete" }
        check(run.completionTicks <= expected.completionTicks + COMPLETION_TICK_SLACK) {
            "${run.scenario}: completion regressed ${expected.completionTicks} -> ${run.completionTicks} ticks"
        }
        check(run.trajectoryFrames <= expected.trajectoryFrames) {
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
        check(run.reroutes <= expected.reroutes) {
            "${run.scenario}: reroutes regressed ${expected.reroutes} -> ${run.reroutes}"
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
        append("\"collisionFrames\":").append(collisionFrames).append(',')
        append("\"bumps\":").append(bumps).append(',')
        append("\"launchMarginFrames\":").append(launchMarginFrames).append(',')
        append("\"planLatencyMs\":").append(planLatencyMs).append(',')
        append("\"reroutes\":").append(reroutes).append(',')
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
            collisionFrames = number("collisionFrames").toInt(),
            bumps = number("bumps").toInt(),
            launchMarginFrames = number("launchMarginFrames").toInt(),
            planLatencyMs = number("planLatencyMs").toLong(),
            reroutes = number("reroutes").toInt(),
            maxReplayDeviation = number("maxReplayDeviation").toDouble(),
        )
    }

    private const val COMPLETION_TICK_SLACK = 8
    private const val MAX_PLAN_LATENCY_MS = 1_500L
}
