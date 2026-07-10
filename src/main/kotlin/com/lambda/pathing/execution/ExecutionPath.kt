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

package com.lambda.pathing.execution

import com.lambda.util.math.lerp
import com.lambda.util.world.FastVector
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

/**
 * Continuous execution representation derived from the current refined path.
 */
data class ExecutionPath(
    val traversalId: Int,
    val sourceNodes: List<FastVector>,
    val segments: List<ExecutionSegment>,
) {
    val isEmpty: Boolean get() = segments.isEmpty()
    val lastSegmentIndex: Int get() = segments.lastIndex

    /**
     * Lookahead point that is allowed to extend INTO the next segment.
     *
     * The per-segment [ExecutionSegment.lookaheadPoint] clamps to the segment
     * end. That works mid-segment but causes a backward steering target when
     * the player has already overshot the end (very common on descend
     * corners). Walking the lookahead distance across segment boundaries
     * keeps the steering target ahead of the player at corner crossings, so
     * the controller never asks the player to reverse.
     *
     * Mirrors Baritone's `fakeDest` pattern (see [MovementDescend.updateState]
     * in the reference implementation): aim past the actual node so the
     * player flows through it instead of decelerating to hit it exactly.
     */
    fun lookaheadAcrossSegments(
        position: Vec3d,
        currentSegmentIndex: Int,
        lookaheadDistance: Double,
    ): Vec3d {
        if (segments.isEmpty()) return position
        val current = segments[currentSegmentIndex.coerceIn(0, lastSegmentIndex)]

        // First, where the projection lands within the current segment.
        val projected = current.projectedDistance(position).coerceAtLeast(0.0)
        var remaining = projected + lookaheadDistance

        if (remaining <= current.horizontalLength) {
            return current.lookaheadPoint(position, lookaheadDistance)
        }

        // Carry the leftover lookahead into subsequent segments.
        remaining -= current.horizontalLength
        var idx = currentSegmentIndex + 1
        while (idx <= lastSegmentIndex) {
            val seg = segments[idx]
            if (remaining <= seg.horizontalLength) {
                val t = if (seg.horizontalLength > 1.0E-9) remaining / seg.horizontalLength else 0.0
                return lerp(t, seg.startPose.position, seg.endPose.position)
            }
            remaining -= seg.horizontalLength
            idx++
        }
        return segments[lastSegmentIndex].endPose.position
    }

    /**
     * Tries to keep the executor anchored to the current segment, but can also
     * skip forward, rewind, or globally relocalize within a bounded radius when
     * the player is displaced.
     */
    fun locateSegment(
        position: Vec3d,
        currentIndex: Int?,
        searchBehind: Int,
        searchAhead: Int,
        corridorRadius: Double,
        verticalTolerance: Double,
        relocalizeDistance: Double,
        backtrackAllowance: Double,
        overshootAllowance: Double,
    ): SegmentSelection? {
        if (segments.isEmpty()) return null

        val minIndex = currentIndex?.let { (it - searchBehind).coerceAtLeast(0) } ?: 0
        val maxIndex = currentIndex?.let { (it + searchAhead).coerceAtMost(lastSegmentIndex) } ?: lastSegmentIndex
        val indices = (minIndex..maxIndex).toList()

        val contained = indices.mapNotNull { index ->
            val segment = segments[index]
            if (!segment.containsPosition(position, corridorRadius, verticalTolerance, backtrackAllowance, overshootAllowance)
                && !segment.hasReached(position, corridorRadius, verticalTolerance)
            ) {
                return@mapNotNull null
            }
            SegmentSelection(index, segment, segment.lateralError(position), segment.remainingDistance(position))
        }

        val selected = when {
            contained.isNotEmpty() -> containedSelection(contained, currentIndex)

            else -> indices.map { index ->
                val segment = segments[index]
                SegmentSelection(index, segment, segment.lateralError(position), segment.remainingDistance(position))
            }.filter {
                it.lateralError <= relocalizeDistance &&
                    abs(it.segment.verticalError(position)) <= verticalTolerance
            }.minByOrNull { relocationScore(it, currentIndex) }
        } ?: return null

        val recoveryMode = when {
            currentIndex == null -> RecoveryMode.Relocalized
            selected.index > currentIndex -> RecoveryMode.SkippedForward
            selected.index < currentIndex -> RecoveryMode.Rewound
            else -> RecoveryMode.KeptCurrent
        }

        return selected.copy(recoveryMode = recoveryMode)
    }

    companion object {
        fun fromNodes(
            traversalId: Int,
            nodes: List<FastVector>,
            /** Chain macro-edge lookup: intermediate landings of (from, to), null for plain edges. */
            maneuverWaypoints: (FastVector, FastVector) -> List<FastVector>? = { _, _ -> null },
            /** Discovered-jump provenance: true if (from, to) is a sim-validated jump edge. */
            discoveredJump: (FastVector, FastVector) -> Boolean = { _, _ -> false },
        ): ExecutionPath {
            val deduplicated = nodes.fold(mutableListOf<FastVector>()) { acc, node ->
                if (acc.lastOrNull() != node) acc += node
                acc
            }

            val segments = deduplicated
                .zipWithNext()
                .mapIndexed { index, (start, end) ->
                    val startPose = ExecutionPose(start)
                    val endPose = ExecutionPose(end)
                    val dy = endPose.position.y - startPose.position.y
                    val chainMids = maneuverWaypoints(start, end)
                    when {
                        chainMids != null -> ChainSegment(
                            index, startPose, endPose,
                            waypoints = chainMids.map { ExecutionPose(it).position },
                        )
                        // Rises above one block are unsupported; drops of any
                        // planned depth execute as walk-offs.
                        dy <= 1.0 + 1.0E-6 -> WalkSegment(
                            index, startPose, endPose,
                            discovered = discoveredJump(start, end),
                        )
                        else -> UnsupportedSegment(index, startPose, endPose, "UnsupportedVertical")
                    }
                }

            return ExecutionPath(
                traversalId = traversalId,
                sourceNodes = deduplicated,
                segments = segments,
            )
        }
    }

    private fun containedSelection(
        candidates: List<SegmentSelection>,
        currentIndex: Int?,
    ): SegmentSelection? {
        if (currentIndex != null) {
            candidates.firstOrNull { it.index == currentIndex }?.let { return it }
        }

        return candidates.minByOrNull { relocationScore(it, currentIndex) }
    }

    private fun relocationScore(
        candidate: SegmentSelection,
        currentIndex: Int?,
    ): Double {
        val indexPenalty = currentIndex?.let { abs(candidate.index - it) * 0.35 } ?: 0.0
        return candidate.lateralError + candidate.remainingDistance * 0.05 + indexPenalty
    }
}

enum class RecoveryMode {
    KeptCurrent,
    SkippedForward,
    Rewound,
    Relocalized,
}

data class SegmentSelection(
    val index: Int,
    val segment: ExecutionSegment,
    val lateralError: Double,
    val remainingDistance: Double,
    val recoveryMode: RecoveryMode = RecoveryMode.KeptCurrent,
)
