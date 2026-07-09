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

package com.lambda.pathing.refinement

import com.lambda.config.blocks.PathRefinementConfig
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.WorldView
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * Greedy post-processing pass that shortens a coarse D* Lite path.
 *
 * Refinement is F2 repair only (research plan §3.0): it removes the
 * 45°-quantization overhead of the coarse grid by replacing runs of
 * same-height nodes with straight any-angle segments, each validated
 * exactly against the swept player footprint ([ShortcutCorridor]). It never
 * crosses a vertical transition — merging steps, drops, and jumps into
 * shortcuts is maneuver-layer work (macro-edges with entry envelopes, WP3),
 * not refinement.
 *
 * Failed corridors leave a *wall memory*: candidates whose segment passes
 * through a recently blocking column are skipped without re-validation,
 * which is what stops the farthest-first descent from probing the same wall
 * dozens of times per anchor.
 */
object PathRefiner {
    private const val EPSILON = 1.0E-9
    private const val PLAYER_HALF_WIDTH = 0.3
    private const val MAX_DEBUG_ATTEMPTS = 96
    private const val WALL_MEMORY_SIZE = 8

    private const val PROFILE_CORRIDOR = "Corridor"

    fun refine(
        view: WorldView,
        coarsePath: List<FastVector>,
        config: PathRefinementConfig,
    ): PathRefinementResult {
        val startedAt = System.nanoTime()
        val debugCollector = DebugCollector(MAX_DEBUG_ATTEMPTS)

        if (!config.enabled || coarsePath.size < 3) {
            return PathRefinementResult(
                path = coarsePath,
                stats = coarsePath.stats(
                    enabled = config.enabled,
                    refinedPath = coarsePath,
                    durationNanos = System.nanoTime() - startedAt,
                ),
                debug = debugCollector.build(),
            )
        }

        val halfWidth = PLAYER_HALF_WIDTH + config.clearanceMargin.coerceAtLeast(0.0)
        val maxLookahead = config.maxLookahead.coerceAtLeast(1)
        val maxChecks = config.maxChecks.coerceAtLeast(1)
        val refined = arrayListOf(coarsePath.first())
        val wallMemory = ArrayDeque<FastVector>()

        var anchorIndex = 0
        var shortcutChecks = 0
        var skippedCandidates = 0
        var budgetExhausted = false

        while (anchorIndex < coarsePath.lastIndex) {
            if (shortcutChecks >= maxChecks) {
                budgetExhausted = true
                anchorIndex = commitAnchor(refined, coarsePath, anchorIndex + 1, anchorIndex)
                appendRemainder(refined, coarsePath, anchorIndex + 1)
                break
            }

            val anchor = coarsePath[anchorIndex]
            val anchorPos = anchor.toFeetCenter()
            val furthestCandidate = (anchorIndex + maxLookahead).coerceAtMost(coarsePath.lastIndex)
            var committedIndex = (anchorIndex + 1).coerceAtMost(coarsePath.lastIndex)
            var foundShortcut = false

            for (candidateIndex in furthestCandidate downTo (anchorIndex + 1)) {
                if (shortcutChecks >= maxChecks) {
                    budgetExhausted = true
                    break
                }

                val candidate = coarsePath[candidateIndex]
                if (!isShortcutCandidate(anchor, candidate)) {
                    skippedCandidates++
                    debugCollector.recordSkipped()
                    continue
                }

                val candidatePos = candidate.toFeetCenter()
                if (hitsRememberedWall(wallMemory, anchorPos, candidatePos, candidate.y, halfWidth)) {
                    skippedCandidates++
                    debugCollector.recordSkipped()
                    continue
                }

                shortcutChecks++
                val evaluation = evaluateCorridorShortcut(
                    view = view,
                    start = anchor,
                    end = candidate,
                    startPos = anchorPos,
                    endPos = candidatePos,
                    halfWidth = halfWidth,
                    wallMemory = wallMemory,
                )
                debugCollector.record(evaluation)

                if (evaluation.traversable) {
                    committedIndex = candidateIndex
                    foundShortcut = true
                    break
                }
            }

            anchorIndex = commitAnchor(
                refined = refined,
                coarsePath = coarsePath,
                requestedIndex = if (foundShortcut) committedIndex else anchorIndex + 1,
                previousAnchorIndex = anchorIndex,
            )

            if (budgetExhausted) {
                appendRemainder(refined, coarsePath, anchorIndex + 1)
                break
            }
        }

        if (!budgetExhausted && refined.last() != coarsePath.last()) {
            refined += coarsePath.last()
        }

        val outputPath = refined.simplifyCollinearSegments()
        return PathRefinementResult(
            path = outputPath,
            stats = coarsePath.stats(
                enabled = true,
                refinedPath = outputPath,
                shortcutChecks = shortcutChecks,
                skippedCandidates = skippedCandidates,
                budgetExhausted = budgetExhausted,
                durationNanos = System.nanoTime() - startedAt,
            ),
            debug = debugCollector.build(),
        )
    }

    /** Same height and an actual horizontal displacement to cut across. */
    private fun isShortcutCandidate(start: FastVector, end: FastVector): Boolean {
        if (start.y != end.y) return false
        val dx = (end.x - start.x).toDouble()
        val dz = (end.z - start.z).toDouble()
        return dx * dx + dz * dz > EPSILON
    }

    /**
     * True if the segment to this candidate passes through a column that
     * already blocked an earlier corridor at the same level — skip it
     * without paying for another validation.
     */
    private fun hitsRememberedWall(
        wallMemory: ArrayDeque<FastVector>,
        startPos: Vec3d,
        endPos: Vec3d,
        candidateY: Int,
        halfWidth: Double,
    ): Boolean = wallMemory.any { wall ->
        wall.y == candidateY && ShortcutCorridor.segmentTouchesColumn(
            startPos.x, startPos.z, endPos.x, endPos.z, wall.x, wall.z, halfWidth,
        )
    }

    private fun rememberWall(wallMemory: ArrayDeque<FastVector>, column: FastVector) {
        if (column in wallMemory) return
        wallMemory += column
        while (wallMemory.size > WALL_MEMORY_SIZE) wallMemory.removeFirst()
    }

    private fun evaluateCorridorShortcut(
        view: WorldView,
        start: FastVector,
        end: FastVector,
        startPos: Vec3d,
        endPos: Vec3d,
        halfWidth: Double,
        wallMemory: ArrayDeque<FastVector>,
    ): ShortcutEvaluation {
        val blocked = ShortcutCorridor.firstBlockedColumn(
            view, startPos.x, startPos.z, endPos.x, endPos.z, start.y, halfWidth,
        )
        if (blocked != null) {
            rememberWall(wallMemory, blocked)
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                reason = ShortcutFailureReason.SweepBlocked,
                bestRemaining = horizontalDistance(blocked.toFeetCenter(), endPos),
            )
        }
        return singleAttemptEvaluation(true, start, end, ShortcutFailureReason.Accepted)
    }

    private fun commitAnchor(
        refined: MutableList<FastVector>,
        coarsePath: List<FastVector>,
        requestedIndex: Int,
        previousAnchorIndex: Int,
    ): Int {
        val nextAnchorIndex = requestedIndex.coerceIn(previousAnchorIndex + 1, coarsePath.lastIndex)
        val nextAnchor = coarsePath[nextAnchorIndex]
        if (refined.last() != nextAnchor) {
            refined += nextAnchor
        }
        return nextAnchorIndex
    }

    private fun appendRemainder(refined: MutableList<FastVector>, coarsePath: List<FastVector>, startIndex: Int) {
        for (index in startIndex..coarsePath.lastIndex) {
            val node = coarsePath[index]
            if (refined.lastOrNull() != node) {
                refined += node
            }
        }
    }

    private fun FastVector.toFeetCenter(): Vec3d = Vec3d.ofBottomCenter(toBlockPos())

    private fun singleAttemptEvaluation(
        traversable: Boolean,
        start: FastVector,
        end: FastVector,
        reason: ShortcutFailureReason,
        bestRemaining: Double = 0.0,
    ) = ShortcutEvaluation(
        traversable = traversable,
        attempts = listOf(
            ShortcutAttemptDebug(
                from = start,
                to = end,
                profile = PROFILE_CORRIDOR,
                accepted = traversable,
                reason = reason.name,
                bestRemaining = bestRemaining,
            )
        ),
    )

    private fun List<FastVector>.stats(
        enabled: Boolean,
        refinedPath: List<FastVector>,
        shortcutChecks: Int = 0,
        skippedCandidates: Int = 0,
        budgetExhausted: Boolean = false,
        durationNanos: Long,
    ) = PathRefinementStats(
        enabled = enabled,
        coarseNodes = size,
        refinedNodes = refinedPath.size,
        coarseLength = pathLength(),
        refinedLength = refinedPath.pathLength(),
        shortcutChecks = shortcutChecks,
        skippedCandidates = skippedCandidates,
        budgetExhausted = budgetExhausted,
        durationMs = durationNanos / 1_000_000.0,
    )

    fun List<FastVector>.pathLength(): Double = zipWithNext { a, b ->
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        sqrt(dx * dx + dy * dy + dz * dz)
    }.sum()

    private fun List<FastVector>.simplifyCollinearSegments(): List<FastVector> {
        if (size <= 2) {
            return this
        }

        val simplified = ArrayList<FastVector>(size)
        simplified += first()

        for (index in 1 until lastIndex) {
            val previous = simplified.last()
            val current = this[index]
            val next = this[index + 1]
            if (!areCollinear(previous, current, next)) {
                simplified += current
            }
        }

        simplified += last()
        return simplified
    }

    private fun horizontalDistance(a: Vec3d, b: Vec3d): Double {
        val dx = b.x - a.x
        val dz = b.z - a.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun areCollinear(a: FastVector, b: FastVector, c: FastVector): Boolean {
        if (a.y != b.y || b.y != c.y) return false

        val abx = b.x - a.x
        val aby = b.y - a.y
        val abz = b.z - a.z
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        val bcz = c.z - b.z

        val crossX = aby * bcz - abz * bcy
        val crossY = abz * bcx - abx * bcz
        val crossZ = abx * bcy - aby * bcx
        if (crossX != 0 || crossY != 0 || crossZ != 0) {
            return false
        }

        val dot = abx * bcx + aby * bcy + abz * bcz
        return dot >= 0
    }

    private data class ShortcutEvaluation(
        val traversable: Boolean,
        val attempts: List<ShortcutAttemptDebug>,
    )

    private class DebugCollector(private val maxRecentAttempts: Int) {
        private val acceptedByProfile = linkedMapOf<String, Int>()
        private val rejectedByReason = linkedMapOf<String, Int>()
        private val recentAttempts = ArrayDeque<ShortcutAttemptDebug>()
        private var acceptedCandidates = 0
        private var rejectedCandidates = 0
        private var skippedCandidates = 0
        private var profileAttempts = 0

        fun recordSkipped() {
            skippedCandidates++
        }

        fun record(evaluation: ShortcutEvaluation) {
            if (evaluation.traversable) {
                acceptedCandidates++
            } else {
                rejectedCandidates++
            }

            for (attempt in evaluation.attempts) {
                profileAttempts++
                recentAttempts += attempt
                while (recentAttempts.size > maxRecentAttempts) {
                    recentAttempts.removeFirst()
                }
                if (attempt.accepted) {
                    acceptedByProfile.increment(attempt.profile)
                } else {
                    rejectedByReason.increment(attempt.reason)
                }
            }
        }

        fun build() = PathRefinementDebug(
            acceptedCandidates = acceptedCandidates,
            rejectedCandidates = rejectedCandidates,
            skippedCandidates = skippedCandidates,
            profileAttempts = profileAttempts,
            acceptedByProfile = acceptedByProfile.toMap(),
            rejectedByReason = rejectedByReason.toMap(),
            recentAttempts = recentAttempts.toList(),
        )

        private fun MutableMap<String, Int>.increment(key: String) {
            this[key] = (this[key] ?: 0) + 1
        }
    }
}
