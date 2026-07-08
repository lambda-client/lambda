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

/**
 * Aggregate metrics for one refinement pass.
 *
 * [shortcutChecks] and [skippedCandidates] are the useful counters when reading
 * HUD data or log output; they correspond to how many candidate segments were
 * actively validated versus skipped before validation.
 */
data class PathRefinementStats(
    val enabled: Boolean = false,
    val coarseNodes: Int = 0,
    val refinedNodes: Int = 0,
    val coarseLength: Double = 0.0,
    val refinedLength: Double = 0.0,
    val shortcutChecks: Int = 0,
    val skippedCandidates: Int = 0,
    val budgetExhausted: Boolean = false,
    val durationMs: Double = 0.0,
) {
    val removedNodes: Int get() = (coarseNodes - refinedNodes).coerceAtLeast(0)
    val savedLength: Double get() = (coarseLength - refinedLength).coerceAtLeast(0.0)
    val savedPercent: Double get() = if (coarseLength > 0.0) savedLength / coarseLength * 100.0 else 0.0
}