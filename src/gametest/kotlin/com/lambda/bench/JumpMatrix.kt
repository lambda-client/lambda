/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.bench

import com.lambda.Lambda.LOG
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One executed jump, keyed to the PLANNED edge that asked for it.
 *
 * The scenario-level pass/fail says whether the agent got to the goal; it says
 * nothing about which maneuver class is unreliable, or why. This record is the
 * unit the reliability program is actually optimizing: a single launch, its
 * entry state, what the executor did at the lip before committing, and where
 * the arc put the feet.
 */
data class JumpRecord(
    val scenario: String,
    /** Executor provenance: "Jump" (discovered), "Chain", or "Walk" (template). */
    val segmentType: String,
    /** Planned edge displacement, rounded to the node lattice. Null if unknown. */
    val edgeDx: Int?,
    val edgeDy: Int?,
    val edgeDz: Int?,
    /** Horizontal speed (stored velocity) on the launch tick. */
    val launchSpeed: Double,
    /** Ticks the executor spent on this segment before it committed the jump. */
    val approachTicks: Int,
    /** Of those, grounded ticks where the jump gate actively refused to fire. */
    val refusalTicks: Int,
    /** The refusal the executor repeated most while stalling at the lip. */
    val dominantGate: String,
    /**
     * Heading change from the previous planned segment into this jump's
     * segment, in degrees.
     *
     * A position-keyed polyline may turn 90 degrees at a node for free; a
     * sprinting player may not. The plan therefore hands the executor takeoffs
     * it can only reach by bleeding off the very momentum the jump needs —
     * which is what the dance at the lip is. This is the number that tests it.
     */
    val approachTurnDegrees: Double?,
    /** 1 for the first launch at this segment, 2+ for retries after a miss. */
    val attemptIndex: Int,
    val success: Boolean,
    val horizontalError: Double?,
    val verticalError: Double?,
    val arcErrMax: Double?,
    val planErrMax: Double?,
) {
    /**
     * Rotation/reflection-canonical class of the planned edge: the physics of
     * a (3,1) jump and a (1,-3) jump are the same jump. Collapsing them is what
     * makes the matrix small enough to read and large enough to be significant.
     */
    val jumpClass: String
        get() {
            if (edgeDx == null || edgeDz == null || edgeDy == null) return "unknown"
            val major = max(abs(edgeDx), abs(edgeDz))
            val minor = minOf(abs(edgeDx), abs(edgeDz))
            val rise = when {
                edgeDy > 0 -> "+$edgeDy"
                edgeDy < 0 -> "$edgeDy"
                else -> "0"
            }
            return "${major}x$minor y$rise"
        }

    /** Entry-speed bucket, 0.04 b/t wide (ground sprint stores ~0.153). */
    val speedBucket: String
        get() {
            val low = floor(launchSpeed / SPEED_BUCKET_WIDTH) * SPEED_BUCKET_WIDTH
            return "%.2f-%.2f".format(low, low + SPEED_BUCKET_WIDTH)
        }

    companion object {
        const val SPEED_BUCKET_WIDTH = 0.04
    }
}

/**
 * Aggregates every jump of a benchmark run into a per-class scoreboard.
 *
 * The gauge that matters is **first-attempt success**: an agent that misses and
 * recovers has still failed the maneuver, and the recovery is exactly the
 * motion waste the field reports. `retries` and `refusalTicks` quantify the two
 * halves of "sloppy execution" — arcs that miss, and launches that dither.
 *
 * Observational in this phase. A class is promotable to a gated regression
 * (repo convention: after consecutive green runs) once [ClassStats.clean] holds
 * — 100% first-attempt success with zero retries.
 */
object JumpMatrix {

    data class ClassStats(
        val jumpClass: String,
        val segmentType: String,
        val attempts: Int,
        val instances: Int,
        val firstAttemptSuccesses: Int,
        val retries: Int,
        val landingErrorMean: Double,
        val landingErrorMax: Double,
        val arcErrorMax: Double,
        val planErrorMax: Double,
        val approachTicksMean: Double,
        val refusalTicksMean: Double,
        val approachTurnMean: Double,
        val gateHistogram: Map<String, Int>,
        val speedBuckets: Map<String, Int>,
        val scenarios: Set<String>,
    ) {
        /** Share of jump instances landed on the FIRST launch. */
        val firstAttemptRate: Double
            get() = if (instances == 0) 1.0 else firstAttemptSuccesses.toDouble() / instances

        /** The promotion criterion: reliable, and reliable without dithering. */
        val clean: Boolean get() = instances > 0 && firstAttemptSuccesses == instances && retries == 0
    }

    fun aggregate(records: List<JumpRecord>): List<ClassStats> = records
        .groupBy { it.jumpClass to it.segmentType }
        .map { (key, group) -> classStats(key.first, key.second, group) }
        .sortedWith(compareBy({ it.firstAttemptRate }, { -it.attempts }))

    private fun classStats(jumpClass: String, segmentType: String, group: List<JumpRecord>): ClassStats {
        // A jump "instance" is one planned edge the agent had to clear. Several
        // records against the same edge mean it missed and tried again — the
        // instance counts once, and only its first attempt counts as success.
        val instances = group.groupBy { it.scenario to (it.edgeDx to it.edgeDz to it.edgeDy) }
        val firstAttempts = group.filter { it.attemptIndex == 1 }
        val landingErrors = group.mapNotNull { it.horizontalError }

        return ClassStats(
            jumpClass = jumpClass,
            segmentType = segmentType,
            attempts = group.size,
            instances = instances.size,
            firstAttemptSuccesses = firstAttempts.count { it.success },
            retries = group.count { it.attemptIndex > 1 },
            landingErrorMean = if (landingErrors.isEmpty()) 0.0 else landingErrors.average(),
            landingErrorMax = landingErrors.maxOrNull() ?: 0.0,
            arcErrorMax = group.mapNotNull { it.arcErrMax }.maxOrNull() ?: 0.0,
            planErrorMax = group.mapNotNull { it.planErrMax }.maxOrNull() ?: 0.0,
            approachTicksMean = group.map { it.approachTicks.toDouble() }.average(),
            refusalTicksMean = group.map { it.refusalTicks.toDouble() }.average(),
            approachTurnMean = group.mapNotNull { it.approachTurnDegrees }
                .ifEmpty { listOf(0.0) }.average(),
            gateHistogram = group.filter { it.refusalTicks > 0 && it.dominantGate.isNotEmpty() }
                .groupingBy { it.dominantGate }.eachCount(),
            speedBuckets = group.groupingBy { it.speedBucket }.eachCount(),
            scenarios = group.map { it.scenario }.toSet(),
        )
    }

    /**
     * First-attempt success bucketed by how sharply the PLAN turns into the
     * takeoff. If the executor were merely mistuned, this would be flat.
     */
    data class TurnBucket(
        val label: String,
        val jumps: Int,
        val firstAttemptSuccesses: Int,
        val refusalTicksMean: Double,
        val landingErrorMean: Double,
    ) {
        val rate: Double get() = if (jumps == 0) 1.0 else firstAttemptSuccesses.toDouble() / jumps
    }

    private val TURN_BANDS = listOf(
        "straight <15" to 15.0,
        "gentle 15-45" to 45.0,
        "hard 45-90" to 90.0,
        "reversal >90" to 181.0,
    )

    fun turnBuckets(records: List<JumpRecord>): List<TurnBucket> {
        val firstAttempts = records.filter { it.attemptIndex == 1 && it.approachTurnDegrees != null }
        var floor = 0.0
        return TURN_BANDS.map { (label, ceiling) ->
            val group = firstAttempts.filter { it.approachTurnDegrees!! >= floor && it.approachTurnDegrees < ceiling }
            floor = ceiling
            TurnBucket(
                label = label,
                jumps = group.size,
                firstAttemptSuccesses = group.count { it.success },
                refusalTicksMean = group.map { it.refusalTicks.toDouble() }.ifEmpty { listOf(0.0) }.average(),
                landingErrorMean = group.mapNotNull { it.horizontalError }.ifEmpty { listOf(0.0) }.average(),
            )
        }
    }

    fun write(directory: File, records: List<JumpRecord>) {
        val stats = aggregate(records)
        val turns = turnBuckets(records)
        File(directory, "jump-matrix.json").writeText(
            buildString {
                append("{\"classes\":[\n")
                append(stats.joinToString(",\n") { it.toJson() })
                append("\n],\"turnBuckets\":[\n")
                append(turns.joinToString(",\n") { bucket ->
                    "  {\"turn\":\"${bucket.label}\",\"jumps\":${bucket.jumps}," +
                        "\"firstAttemptSuccesses\":${bucket.firstAttemptSuccesses}," +
                        "\"firstAttemptRate\":${"%.3f".format(bucket.rate)}," +
                        "\"refusalTicksMean\":${"%.1f".format(bucket.refusalTicksMean)}," +
                        "\"landingErrorMean\":${"%.3f".format(bucket.landingErrorMean)}}"
                })
                append("\n]}\n")
            }
        )
        if (stats.isNotEmpty()) LOG.info("\n" + renderTable(stats, records, turns))
    }

    private fun ClassStats.toJson(): String = buildString {
        append("  {\"jumpClass\":\"").append(jumpClass).append('"')
        append(",\"segmentType\":\"").append(segmentType).append('"')
        append(",\"attempts\":").append(attempts)
        append(",\"instances\":").append(instances)
        append(",\"firstAttemptSuccesses\":").append(firstAttemptSuccesses)
        append(",\"firstAttemptRate\":").append("%.3f".format(firstAttemptRate))
        append(",\"retries\":").append(retries)
        append(",\"clean\":").append(clean)
        append(",\"landingErrorMean\":").append("%.3f".format(landingErrorMean))
        append(",\"landingErrorMax\":").append("%.3f".format(landingErrorMax))
        append(",\"arcErrorMax\":").append("%.3f".format(arcErrorMax))
        append(",\"planErrorMax\":").append("%.3f".format(planErrorMax))
        append(",\"approachTicksMean\":").append("%.1f".format(approachTicksMean))
        append(",\"refusalTicksMean\":").append("%.1f".format(refusalTicksMean))
        append(",\"approachTurnMean\":").append("%.1f".format(approachTurnMean))
        append(",\"gates\":{")
        append(gateHistogram.entries.sortedByDescending { it.value }
            .joinToString(",") { "\"${it.key}\":${it.value}" })
        append("},\"speedBuckets\":{")
        append(speedBuckets.entries.sortedBy { it.key }
            .joinToString(",") { "\"${it.key}\":${it.value}" })
        append("},\"scenarios\":[")
        append(scenarios.sorted().joinToString(",") { "\"$it\"" })
        append("]}")
    }

    private fun renderTable(
        stats: List<ClassStats>,
        records: List<JumpRecord>,
        turns: List<TurnBucket>,
    ): String = buildString {
        val landed = records.count { it.success }
        val firstTry = stats.sumOf { it.firstAttemptSuccesses }
        val instances = stats.sumOf { it.instances }
        appendLine("[Bench] ==== JUMP RELIABILITY MATRIX ====")
        appendLine(
            "[Bench] %d jumps over %d planned edges — first-attempt %d/%d (%.1f%%), landings %d/%d".format(
                records.size, instances, firstTry, instances,
                if (instances == 0) 100.0 else firstTry * 100.0 / instances,
                landed, records.size,
            )
        )
        appendLine(
            "[Bench] %-10s %-6s %5s %5s %8s %7s %6s %6s %6s  %s".format(
                "class", "type", "inst", "try1", "rate", "retry", "land", "align", "turn", "top refusal",
            )
        )
        for (row in stats) {
            val topGate = row.gateHistogram.maxByOrNull { it.value }
            appendLine(
                "[Bench] %-10s %-6s %5d %5d %7.1f%% %7d %6.2f %6.1f %5.0f°  %s".format(
                    row.jumpClass,
                    row.segmentType.take(6),
                    row.instances,
                    row.firstAttemptSuccesses,
                    row.firstAttemptRate * 100.0,
                    row.retries,
                    row.landingErrorMax,
                    row.refusalTicksMean,
                    row.approachTurnMean,
                    topGate?.let { "${it.key} x${it.value}" } ?: "-",
                )
            )
        }
        // The plan's turning dynamics vs. the agent's. A position-keyed
        // polyline turns for free at a node; a sprinting player does not.
        appendLine("[Bench] ---- first-attempt success by PLANNED approach turn ----")
        for (bucket in turns) {
            if (bucket.jumps == 0) continue
            appendLine(
                "[Bench] %-14s %3d jumps  %6.1f%% first-try  align %4.1f ticks  landErr %.2f".format(
                    bucket.label, bucket.jumps, bucket.rate * 100.0,
                    bucket.refusalTicksMean, bucket.landingErrorMean,
                )
            )
        }
        val dirty = stats.filterNot(ClassStats::clean)
        if (dirty.isEmpty()) {
            appendLine("[Bench] every jump class is clean (100% first-attempt, no retries)")
        } else {
            appendLine(
                "[Bench] %d/%d classes NOT clean: %s".format(
                    dirty.size, stats.size,
                    dirty.joinToString(", ") {
                        "${it.jumpClass}/${it.segmentType}(${(it.firstAttemptRate * 100).roundToInt()}%)"
                    },
                )
            )
        }
    }
}
