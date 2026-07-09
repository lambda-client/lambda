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

import kotlin.math.max
import kotlin.math.sqrt

/**
 * The one source of movement edge costs, shared by the template library and
 * the reference walking model. Unit: expected game ticks (WP3.1/F1 — the
 * fixed common currency every layer sums, research plan §4.2), derived from
 * the measured [MoveRates] instead of hand-tuned relative units.
 *
 * Costing policy mirrors the executor: flat runs sprint; step-ups pay the
 * measured sustained climb rate; walk-off drops pay the fall time, which
 * runs concurrently with the forward step (max, not sum); gap jumps pay
 * their arc time.
 */
object MoveCosts {
    /** Flat cardinal step, executed sprinting: ≈ 3.56 ticks. */
    val CARDINAL = 1.0 / MoveRates.SPRINT_BPT

    val DIAGONAL = sqrt(2.0) * CARDINAL

    /**
     * One-block step-up: the measured sustained staircase rate — ≈ 10
     * ticks per block climbed, forward progress of the arc included.
     */
    val STEP_UP = 1.0 / MoveRates.JUMP_UP_ASCENT_BPT

    /**
     * Gap jumps (2 forward, ±0/+1): the arc time measured on the S3
     * gauntlets — ~11–12 airborne ticks takeoff to touchdown at the
     * executor's takeoff policy (flat gaps walk, rising gaps sprint).
     */
    val JUMP = 12.0
    val JUMP_UP = 12.0

    private const val DROP_LANDING_TICKS = 1.0

    /**
     * Walk-off drop of [depth] blocks: the fall runs concurrently with the
     * forward step, so the edge costs the longer of the two plus a landing
     * tick. Deeper drops pay less per block (fall acceleration) — the
     * asymmetry the plan's drop primitives exist for.
     */
    fun drop(depth: Int): Double = max(CARDINAL, MoveRates.fallTicks(depth)) + DROP_LANDING_TICKS

    /** Walk-off descent of one block. */
    val STEP_DOWN = drop(1)
}
