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

import net.minecraft.util.math.Vec3d

/**
 * The predicted flight path of one maneuver segment: per-tick feet positions
 * from a forward simulation run with the same entry assumptions the executor
 * plays back. Computed once per adopted path (PathfinderExecutor) and drawn
 * by the renderer so the *plan* — not just the line between nodes — is
 * visible before the agent commits to it.
 */
data class PlannedArc(
    val segmentIndex: Int,
    val kind: Kind,
    /** Per-tick feet positions, starting at the takeoff stance. */
    val points: List<Vec3d>,
    /** The planned landing point (segment end node center). */
    val landing: Vec3d,
) {
    enum class Kind { GapJump, RisingGap, StepUp, Drop, Chain }
}
