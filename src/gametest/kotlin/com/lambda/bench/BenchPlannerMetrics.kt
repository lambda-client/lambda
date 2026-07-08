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

import com.lambda.pathing.metrics.PlannerMetrics
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos

/**
 * Per-scenario [PlannerMetrics] sink: buffers every planner event as a JSON
 * line (spec §4 vocabulary) and aggregates the distributions the regression
 * gates care about — repair wall time and expansions.
 *
 * Planner callbacks arrive on the client thread while the runner reads
 * results from the test thread, hence the synchronization.
 */
class BenchPlannerMetrics : PlannerMetrics {
    private val startNanos = System.nanoTime()
    private val lines = StringBuilder()

    private val repairWallMicros = ArrayList<Long>()
    private val repairExpansions = ArrayList<Long>()
    private val syncWallMicros = ArrayList<Long>()

    var initialWallMicros: Long = -1
        private set
    var initialExpansions: Int = -1
        private set
    var initialTimedOut: Boolean = false
        private set
    var initialReachedGoal: Boolean = false
        private set
    var edgeObstructedReports: Int = 0
        private set

    private fun tsMicros(): Long = (System.nanoTime() - startNanos) / 1_000

    @Synchronized
    private fun emit(event: String, fields: String) {
        lines.append("{\"ts_us\":").append(tsMicros())
            .append(",\"ev\":\"").append(event).append('"')
        if (fields.isNotEmpty()) lines.append(',').append(fields)
        lines.append("}\n")
    }

    private fun FastVector.short(): String {
        val b = toBlockPos()
        return "${b.x},${b.y},${b.z}"
    }

    @Synchronized
    override fun planStart(traversalId: Int, start: FastVector, goal: FastVector) =
        emit("plan_start", "\"id\":$traversalId,\"start\":\"${start.short()}\",\"goal\":\"${goal.short()}\"")

    @Synchronized
    override fun initialPath(
        traversalId: Int,
        expansions: Int,
        wallMicros: Long,
        timedOut: Boolean,
        coarseNodes: Int,
        reachedGoal: Boolean,
    ) {
        initialWallMicros = wallMicros
        initialExpansions = expansions
        initialTimedOut = timedOut
        initialReachedGoal = reachedGoal
        emit(
            "initial_path",
            "\"id\":$traversalId,\"expansions\":$expansions,\"wall_us\":$wallMicros," +
                "\"timed_out\":$timedOut,\"coarse_nodes\":$coarseNodes,\"reached_goal\":$reachedGoal",
        )
    }

    @Synchronized
    override fun repairEnd(
        traversalId: Int,
        expansions: Int,
        wallMicros: Long,
        timedOut: Boolean,
        coarseNodes: Int,
        reachedGoal: Boolean,
    ) {
        repairWallMicros += wallMicros
        repairExpansions += expansions.toLong()
        emit(
            "repair_end",
            "\"id\":$traversalId,\"expansions\":$expansions,\"wall_us\":$wallMicros," +
                "\"timed_out\":$timedOut,\"coarse_nodes\":$coarseNodes,\"reached_goal\":$reachedGoal",
        )
    }

    @Synchronized
    override fun syncEnd(
        traversalId: Int,
        nodesChecked: Int,
        edgesAdded: Int,
        edgesRemoved: Int,
        edgesChanged: Int,
        wallMicros: Long,
    ) {
        syncWallMicros += wallMicros
        emit(
            "sync_end",
            "\"id\":$traversalId,\"nodes_checked\":$nodesChecked,\"edges_added\":$edgesAdded," +
                "\"edges_removed\":$edgesRemoved,\"edges_changed\":$edgesChanged,\"wall_us\":$wallMicros",
        )
    }

    @Synchronized
    override fun refineEnd(
        traversalId: Int,
        coarseNodes: Int,
        refinedNodes: Int,
        shortcutChecks: Int,
        accepted: Int,
        rejected: Int,
        durationMs: Double,
    ) = emit(
        "refine_end",
        "\"id\":$traversalId,\"coarse_nodes\":$coarseNodes,\"refined_nodes\":$refinedNodes," +
            "\"checks\":$shortcutChecks,\"accepted\":$accepted,\"rejected\":$rejected," +
            "\"duration_ms\":${"%.3f".format(durationMs)}",
    )

    @Synchronized
    override fun edgeObstructed(traversalId: Int, from: FastVector, to: FastVector, penaltyFactor: Double) {
        edgeObstructedReports++
        emit(
            "edge_obstructed",
            "\"id\":$traversalId,\"from\":\"${from.short()}\",\"to\":\"${to.short()}\"," +
                "\"factor\":${"%.0f".format(penaltyFactor)}",
        )
    }

    @Synchronized
    override fun traversalEnd(traversalId: Int, status: String) =
        emit("traversal_end", "\"id\":$traversalId,\"status\":\"$status\"")

    @Synchronized
    fun jsonLines(): String = lines.toString()

    @Synchronized
    fun stats(): PlannerStats = PlannerStats(
        initialWallMicros = initialWallMicros,
        initialExpansions = initialExpansions,
        initialTimedOut = initialTimedOut,
        repairs = repairWallMicros.size,
        repairWallUsP50 = percentile(repairWallMicros, 50.0),
        repairWallUsP95 = percentile(repairWallMicros, 95.0),
        repairWallUsMax = repairWallMicros.maxOrNull() ?: 0,
        repairExpansionsP50 = percentile(repairExpansions, 50.0),
        repairExpansionsMax = repairExpansions.maxOrNull() ?: 0,
        syncs = syncWallMicros.size,
        syncWallUsP50 = percentile(syncWallMicros, 50.0),
        syncWallUsMax = syncWallMicros.maxOrNull() ?: 0,
        edgeObstructedReports = edgeObstructedReports,
    )

    /** Planner-side distribution summary for one scenario run. */
    data class PlannerStats(
        val initialWallMicros: Long,
        val initialExpansions: Int,
        val initialTimedOut: Boolean,
        val repairs: Int,
        val repairWallUsP50: Long,
        val repairWallUsP95: Long,
        val repairWallUsMax: Long,
        val repairExpansionsP50: Long,
        val repairExpansionsMax: Long,
        val syncs: Int,
        val syncWallUsP50: Long,
        val syncWallUsMax: Long,
        val edgeObstructedReports: Int,
    ) {
        fun toJsonFields(): String = buildString {
            append("\"initialWallUs\":").append(initialWallMicros)
            append(",\"initialExpansions\":").append(initialExpansions)
            append(",\"initialTimedOut\":").append(initialTimedOut)
            append(",\"repairs\":").append(repairs)
            append(",\"repairWallUsP50\":").append(repairWallUsP50)
            append(",\"repairWallUsP95\":").append(repairWallUsP95)
            append(",\"repairWallUsMax\":").append(repairWallUsMax)
            append(",\"repairExpansionsP50\":").append(repairExpansionsP50)
            append(",\"repairExpansionsMax\":").append(repairExpansionsMax)
            append(",\"syncs\":").append(syncs)
            append(",\"syncWallUsP50\":").append(syncWallUsP50)
            append(",\"syncWallUsMax\":").append(syncWallUsMax)
            append(",\"edgeObstructedReports\":").append(edgeObstructedReports)
        }

        companion object {
            val EMPTY = PlannerStats(
                initialWallMicros = -1,
                initialExpansions = -1,
                initialTimedOut = false,
                repairs = 0,
                repairWallUsP50 = 0,
                repairWallUsP95 = 0,
                repairWallUsMax = 0,
                repairExpansionsP50 = 0,
                repairExpansionsMax = 0,
                syncs = 0,
                syncWallUsP50 = 0,
                syncWallUsMax = 0,
                edgeObstructedReports = 0,
            )
        }
    }

    companion object {
        /**
         * Nearest-rank percentile — the spec's aggregation primitive. Small
         * sample counts per scenario make interpolation false precision.
         */
        fun percentile(values: List<Long>, p: Double): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
