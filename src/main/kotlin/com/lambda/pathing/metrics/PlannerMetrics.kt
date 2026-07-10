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

package com.lambda.pathing.metrics

import com.lambda.util.world.FastVector

/**
 * Event sink for planner instrumentation (benchmark harness spec §4).
 *
 * Production installs nothing and pays only the no-op virtual calls; the
 * benchmark harness installs a JSONL writer per scenario. Event names and
 * field meanings follow the harness spec vocabulary — extend when new work
 * packages land, never rename: dashboards and regression gates key on them.
 *
 * All callbacks fire on the client/planner thread; implementations that read
 * results from another thread must synchronize internally.
 */
interface PlannerMetrics {
    /** A traversal session was created: `plan_start`. */
    fun planStart(traversalId: Int, start: FastVector, goal: FastVector) {}

    /** Why a planner budget slice is about to run: `compute_start`. */
    fun computeStart(traversalId: Int, cause: String, start: FastVector, graphSize: Int) {}

    /**
     * A chunk transition invalidated snapshot sections the planner had actually
     * read: `chunk_visibility`. Unobserved chunk events are deliberately not
     * reported because no graph state can depend on them.
     */
    fun chunkVisibility(
        traversalId: Int,
        chunkX: Int,
        chunkZ: Int,
        loaded: Boolean,
        evictedSections: Int,
    ) {}

    /**
     * First `computeShortestPath` of a session finished: `initial_path`.
     * [expansions] = queue nodes processed, [wallMicros] = wall time of the
     * compute call only (refinement is reported separately).
     */
    fun initialPath(
        traversalId: Int,
        expansions: Int,
        wallMicros: Long,
        timedOut: Boolean,
        coarseNodes: Int,
        reachedGoal: Boolean,
    ) {}

    /** Any subsequent compute (start move, world change, feedback): `repair_end`. */
    fun repairEnd(
        traversalId: Int,
        expansions: Int,
        wallMicros: Long,
        timedOut: Boolean,
        coarseNodes: Int,
        reachedGoal: Boolean,
    ) {}

    /** Graph resynchronization after a world change: `sync_end`. */
    fun syncEnd(
        traversalId: Int,
        nodesChecked: Int,
        edgesAdded: Int,
        edgesRemoved: Int,
        edgesChanged: Int,
        wallMicros: Long,
    ) {}

    /** Coarse-path refinement completed (not emitted on cache reuse): `refine_end`. */
    fun refineEnd(
        traversalId: Int,
        coarseNodes: Int,
        refinedNodes: Int,
        shortcutChecks: Int,
        accepted: Int,
        rejected: Int,
        durationMs: Double,
    ) {}

    /** Executor reported a planned edge as untraversable (§4.6 feedback): `edge_obstructed`. */
    fun edgeObstructed(traversalId: Int, from: FastVector, to: FastVector, penaltyFactor: Double) {}

    /** Terminal status transition (Succeeded/Cancelled): `traversal_end`. */
    fun traversalEnd(traversalId: Int, status: String) {}

    /** The no-op production sink. */
    object NoOp : PlannerMetrics

    companion object {
        /**
         * The active sink. Written by the harness at scenario boundaries,
         * read by the planner on every instrumented operation.
         */
        @JvmStatic
        @Volatile
        var sink: PlannerMetrics = NoOp
    }
}
