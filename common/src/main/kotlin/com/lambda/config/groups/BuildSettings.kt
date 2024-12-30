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

package com.lambda.config.groups

import com.lambda.config.Configurable

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    enum class Page {
        GENERAL, BREAK, PLACE
    }

    private val page by c.setting("Build Page", Page.GENERAL, "Current page", vis)

    // General
    override val pathing by c.setting("Pathing", true, "Path to blocks") { vis() && page == Page.GENERAL }
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks") { vis() && page == Page.GENERAL && pathing }
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks") { vis() && page == Page.GENERAL }

    // Breaking
    override val rotateForBreak by c.setting("Rotate For Break", true, "Rotate towards block while breaking") { vis() && page == Page.BREAK }
    override val breakConfirmation by c.setting("Break Confirmation", false, "Wait for block break confirmation") { vis() && page == Page.BREAK }
    override val maxPendingBreaks by c.setting("Max Pending Breaks", 1, 1..10, 1, "Maximum pending block breaks") { vis() && page == Page.BREAK }
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick") { vis() && page == Page.BREAK }
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)") { vis() && page == Page.BREAK }
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks") { vis() && page == Page.BREAK }

    // Placing
    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() && page == Page.PLACE }
    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation") { vis() && page == Page.PLACE }
    override val placeTimeout by c.setting("Place Timeout", 10, 1..30, 1, "Timeout for block placement in ticks", unit = " ticks") { vis() && page == Page.PLACE && placeConfirmation }
    override val maxPendingPlacements by c.setting("Max Pending Places", 1, 1..10, 1, "Maximum pending block places") { vis() && page == Page.PLACE }
    override val placementsPerTick by c.setting("Instant Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick") { vis() && page == Page.PLACE }
}
