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

package com.lambda.pathing.primitives

import kotlin.math.sqrt

/**
 * The one source of movement edge costs, shared by the template library and
 * the reference walking model. Values are hand-tuned relative units for now;
 * the WP0 calibration pass replaces them with simulator-measured
 * tick-denominated constants (single-source is what makes that swap safe).
 */
object MoveCosts {
    const val CARDINAL = 1.0
    val DIAGONAL = sqrt(2.0)

    /**
     * Step-up costs more than flat to discourage unnecessary stairs; jumping
     * takes ~10 ticks to clear a block while flat sprint covers it in ~7.
     */
    const val STEP_UP = 1.5

    /**
     * Step-down is slightly costlier than flat to prefer level paths when
     * both exist (avoids the bot spamming small drops to skim corners).
     */
    const val STEP_DOWN = 1.05

    /** Gap jump costs — more expensive than walking to avoid pointless jumping. */
    const val JUMP = 2.2
    const val JUMP_UP = 2.5

    /** Fall time is terminal-velocity bound: deeper drops pay less per block. */
    const val DROP_PER_BLOCK = 0.35

    /**
     * Cost of a walk-off descent of [depth] blocks. Depth 1 keeps its legacy
     * tuned value; deeper drops pay a base plus fall time that grows slower
     * than linearly per block, which keeps a real drop cheaper than a long
     * staircase detour of the same height.
     */
    fun drop(depth: Int): Double =
        if (depth <= 1) STEP_DOWN else STEP_DOWN + DROP_PER_BLOCK * depth
}
