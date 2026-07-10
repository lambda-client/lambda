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

package com.lambda.pathing.manager

import com.lambda.config.blocks.PlannerConfig
import com.lambda.pathing.goal.TraversalGoal
import com.lambda.pathing.metrics.PlannerMetrics
import com.lambda.pathing.refinement.PathRefinementDebug
import com.lambda.pathing.refinement.PathRefinementStats
import com.lambda.pathing.refinement.PathRefiner.pathLength
import com.lambda.util.FormattingUtils.format
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos

class TraversalHandle internal constructor(
    val id: Int,
    val owner: Any?,
    val goal: TraversalGoal,
    val config: PlannerConfig,
) {
    // Written by the planner worker, read by the executor/render/task code
    // on the client thread — every published field is volatile and holds
    // immutable values only.
    @Volatile var status: Status = Status.Planning
        internal set

    /** One atomic publication prevents a path from being paired with edge
     * provenance from the preceding/following worker result. */
    @Volatile private var publishedPlan: PublishedPlan = PublishedPlan()
    internal val planSnapshot: PublishedPlan get() = publishedPlan

    val path: List<FastVector> get() = publishedPlan.path
    val coarsePath: List<FastVector> get() = publishedPlan.coarsePath
    val edgeAnnotations: Map<Pair<FastVector, FastVector>, EdgeAnnotation>
        get() = publishedPlan.edgeAnnotations

    @Volatile var graphSize: Int = 0
        internal set

    @Volatile var processedNodes: Int = 0
        internal set

    @Volatile var lastSynchronization: SynchronizationStats = SynchronizationStats()
        internal set

    @Volatile var lastRefinement: PathRefinementStats = PathRefinementStats()
        internal set

    @Volatile var lastRefinementDebug: PathRefinementDebug = PathRefinementDebug()
        internal set

    @Volatile var failureReason: String? = null
        internal set

    val pathLength: Double
        get() = path.pathLength()

    internal fun publishPlan(
        coarsePath: List<FastVector>,
        path: List<FastVector>,
        edgeAnnotations: Map<Pair<FastVector, FastVector>, EdgeAnnotation>,
    ) {
        // Preserve list identity across equal republications: the executor
        // keys its rebuild (and the segment-progress reset it implies) on
        // the path instance, and steady-state repairs republish an unchanged
        // route many times per traversal.
        val current = publishedPlan
        publishedPlan = PublishedPlan(
            coarsePath = if (current.coarsePath == coarsePath) current.coarsePath else coarsePath.toList(),
            path = if (current.path == path) current.path else path.toList(),
            edgeAnnotations = if (current.edgeAnnotations == edgeAnnotations) {
                current.edgeAnnotations
            } else {
                edgeAnnotations.toMap()
            },
        )
    }

    fun cancel() {
        if (status.isTerminal) return
        status = Status.Cancelled
        PlannerMetrics.sink.traversalEnd(id, status.name)
    }

    internal fun succeed() {
        if (status.isTerminal) return
        status = Status.Succeeded
        failureReason = null
        PlannerMetrics.sink.traversalEnd(id, status.name)
    }

    fun debugString() = buildString {
        appendLine("Traversal #$id: $status")
        appendLine("Goal: $goal")
        appendLine("Path nodes: ${path.size} (${pathLength.format()} blocks)")
        if (lastRefinement.enabled) {
            appendLine("Coarse nodes: ${coarsePath.size} (${lastRefinement.coarseLength.format()} blocks)")
            appendLine("Refinement: -${lastRefinement.removedNodes} nodes, -${lastRefinement.savedLength.format()} blocks (${lastRefinement.savedPercent.format()}%)")
            appendLine("Refinement checks: ${lastRefinement.shortcutChecks}, accepted ${lastRefinementDebug.acceptedCandidates}, rejected ${lastRefinementDebug.rejectedCandidates}, profile attempts ${lastRefinementDebug.profileAttempts}")
            lastRefinementDebug.lastAttempt?.let { attempt ->
                appendLine("Last refinement attempt: ${attempt.profile} ${if (attempt.accepted) "accepted" else "rejected"} (${attempt.reason})")
            }
        }
        appendLine("Graph nodes: $graphSize")
        appendLine("Processed nodes: $processedNodes")
        appendLine("Last sync: $lastSynchronization")
        failureReason?.let { appendLine("Failure: $it") }
        if (path.isNotEmpty()) {
            appendLine("Start: ${path.first().toBlockPos().toShortString()}")
            appendLine("End: ${path.last().toBlockPos().toShortString()}")
        }
    }.trimEnd()

    enum class Status {
        Planning,
        Ready,
        Partial,
        Succeeded,
        Failed,
        Cancelled;

        val isTerminal get() = this == Succeeded || this == Cancelled
    }

    data class SynchronizationStats(
        val nodesChecked: Int = 0,
        val edgesAdded: Int = 0,
        val edgesRemoved: Int = 0,
        val edgesChanged: Int = 0,
    )

    /** Executor-facing provenance of one path edge. */
    data class EdgeAnnotation(
        val chainWaypoints: List<FastVector>? = null,
        val discoveredJump: Boolean = false,
    )

    internal data class PublishedPlan(
        val coarsePath: List<FastVector> = emptyList(),
        val path: List<FastVector> = emptyList(),
        val edgeAnnotations: Map<Pair<FastVector, FastVector>, EdgeAnnotation> = emptyMap(),
    )
}
