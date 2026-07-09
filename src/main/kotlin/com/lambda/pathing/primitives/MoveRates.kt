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

/**
 * Measured movement rates — the WP0.6 calibration constants the cost model
 * and the T1 heuristic derivations consume. Never hand-edit a number here
 * without re-running the calibration pass; the whole point of this file is
 * that every figure has a measurement behind it.
 *
 * Provenance: `runClientGameTest` calibration pass, runs 20260709-043656
 * and 20260709-051520 (bit-identical), Minecraft 1.21.11, singleplayer,
 * 20-tick sliding-window sustained rates (see `calibration.json` in the
 * benchmark output for the raw report). Blocks per tick.
 */
object MoveRates {
    /** Sustained flat walk. Nominal community value: 0.216. */
    const val WALK_BPT = 0.2159

    /** Sustained flat sprint. Nominal: 0.281. */
    const val SPRINT_BPT = 0.2806

    /**
     * Sustained flat sprint-jumping. Nominal: ~0.356. Exceeds sprint by
     * ~30% — the T1 action item: once sprint-jump maneuver edges exist
     * (WP3.2), the horizontal heuristic cap must come from THIS rate.
     * [MoveTable.deriveCaps] picks it up automatically when such an edge
     * enters the template/maneuver set.
     */
    const val SPRINT_JUMP_BPT = 0.3640

    /**
     * Sustained vertical rate of repeated 1-block jump-ups on a staircase
     * (2-wide treads, jump key held). Includes the concurrent forward
     * progress of each arc — it is the honest per-step cost of climbing.
     */
    const val JUMP_UP_ASCENT_BPT = 0.1000

    /**
     * Highest observed fall speed over a ~120-block drop; a lower bound of
     * the 3.92 asymptote. Descent heuristic caps derive from edge costs
     * (T1), so this is reference/monitoring data, not a planner input.
     */
    const val TERMINAL_FALL_BPT = 3.4501

    private const val MAX_FALL_TABLE_DEPTH = 12

    /**
     * Ticks for a from-rest fall of whole-block depths, from the vanilla
     * closed form (per tick: v = (v + 0.08) · 0.98, then y += v). This is
     * exact — vertical motion accepts no player input — so it needs no
     * empirical run; the free-fall calibration only cross-checks the drag
     * model via [TERMINAL_FALL_BPT].
     */
    private val FALL_TICKS: DoubleArray = run {
        val table = DoubleArray(MAX_FALL_TABLE_DEPTH + 1)
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        while (depth <= MAX_FALL_TABLE_DEPTH) {
            tick++
            velocity = (velocity + 0.08) * 0.98
            fallen += velocity
            while (depth <= MAX_FALL_TABLE_DEPTH && fallen >= depth) {
                table[depth] = tick.toDouble()
                depth++
            }
        }
        table
    }

    /** Ticks to fall [depth] blocks from rest (clamped to the table). */
    fun fallTicks(depth: Int): Double = FALL_TICKS[depth.coerceIn(1, MAX_FALL_TABLE_DEPTH)]
}
