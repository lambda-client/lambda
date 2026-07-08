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
    var status: Status = Status.Planning
        internal set

    var path: List<FastVector> = emptyList()
        internal set

    var coarsePath: List<FastVector> = emptyList()
        internal set

    var graphSize: Int = 0
        internal set

    var processedNodes: Int = 0
        internal set

    var lastSynchronization: SynchronizationStats = SynchronizationStats()
        internal set

    var lastRefinement: PathRefinementStats = PathRefinementStats()
        internal set

    var lastRefinementDebug: PathRefinementDebug = PathRefinementDebug()
        internal set

    var failureReason: String? = null
        internal set

    val pathLength: Double
        get() = path.pathLength()

    fun cancel() {
        if (status.isTerminal) return
        status = Status.Cancelled
    }

    internal fun succeed() {
        if (status.isTerminal) return
        status = Status.Succeeded
        failureReason = null
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
}
