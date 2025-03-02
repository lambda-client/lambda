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

class PathingSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : PathingConfig {
    enum class Page {
        Execution, Misc
    }

    private val page by c.setting("Pathing Page", Page.Execution, "Current page", vis)

    override val kP by c.setting("P Gain", 0.5, 0.0..2.0, 0.01) { vis() && page == Page.Execution }
    override val kI by c.setting("I Gain", 0.0, 0.0..1.0, 0.01) { vis() && page == Page.Execution }
    override val kD by c.setting("D Gain", 0.2, 0.0..1.0, 0.01) { vis() && page == Page.Execution }
    override val tolerance by c.setting("Node Tolerance", 0.1, 0.01..1.0, 0.01) { vis() && page == Page.Execution }
    override val cutoffTimeout by c.setting("Cutoff Timeout", 50L, 1L..2000L, 10L) { vis() && page == Page.Execution }
    override val shortcutLength by c.setting("Shortcut Length", 10, 1..100, 1) { vis() && page == Page.Execution }
    override val pathClearanceCheckDistance by c.setting("Path Clearance Check Distance", 0.3, 0.0..1.0, 0.01) { vis() && page == Page.Execution }

    override val assumeJesus by c.setting("Assume Jesus", false) { vis() && page == Page.Misc }
}
