/*
 * Copyright 2024 Lambda
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

package com.lambda.pathing

import com.lambda.config.Configurable
import com.lambda.config.groups.RotationSettings

class PathingSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : PathingConfig {
    enum class Page {
        Pathfinding, Refinement, Movement, Rotation, Misc
    }

    private val page by c.setting("Pathing Page", Page.Pathfinding, "Current page", vis)

    override val algorithm by c.setting("Algorithm", PathingConfig.PathingAlgorithm.A_STAR) { vis() && page == Page.Pathfinding }
    override val cutoffTimeout by c.setting("Cutoff Timeout", 500L, 1L..2000L, 10L, "Timeout of path calculation", " ms") { vis() && page == Page.Pathfinding }
    override val maxFallHeight by c.setting("Max Fall Height", 3.0, 0.0..30.0, 0.5) { vis() && page == Page.Pathfinding }

    override val refinePath by c.setting("Refine Path with θ*", true) { vis() && page == Page.Refinement }
    override val shortcutLength by c.setting("Shortcut Length", 15, 1..100, 1) { vis() && refinePath && page == Page.Refinement }
    override val clearancePrecision by c.setting("Clearance Precision", 0.1, 0.0..1.0, 0.01) { vis() && refinePath && page == Page.Refinement }
    override val findShortcutJumps by c.setting("Find Shortcut Jumps", true) { vis() && refinePath && page == Page.Refinement }

    override val moveAlongPath by c.setting("Move Along Path", true) { vis() && page == Page.Movement }
    override val kP by c.setting("P Gain", 0.5, 0.0..2.0, 0.01) { vis() && moveAlongPath && page == Page.Movement }
    override val kI by c.setting("I Gain", 0.0, 0.0..1.0, 0.01) { vis() && moveAlongPath && page == Page.Movement }
    override val kD by c.setting("D Gain", 0.2, 0.0..1.0, 0.01) { vis() && moveAlongPath && page == Page.Movement }
    override val tolerance by c.setting("Node Tolerance", 0.6, 0.01..2.0, 0.05) { vis() && moveAlongPath && page == Page.Movement }
    override val allowSprint by c.setting("Allow Sprint", true) { vis() && moveAlongPath && page == Page.Movement }

    override val rotation = RotationSettings(c) { page == Page.Rotation }

    override val renderCoarsePath by c.setting("Render Coarse Path", false) { vis() && page == Page.Misc }
    override val renderRefinedPath by c.setting("Render Refined Path", true) { vis() && page == Page.Misc }
    override val renderGoal by c.setting("Render Goal", true) { vis() && page == Page.Misc }
    override val assumeJesus by c.setting("Assume Jesus", false) { vis() && page == Page.Misc }
}
