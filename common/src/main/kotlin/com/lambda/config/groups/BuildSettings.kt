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

    override val pathing by c.setting("Pathing", true, "Path to blocks") { vis() && page == Page.GENERAL }

    override val breakConfirmation by c.setting("Break Confirmation", false, "Wait for block break confirmation") { vis() && page == Page.BREAK }
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)") { vis() && page == Page.BREAK }
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 10, 1..30, 1, "Maximum instant block breaks per tick") { vis() && page == Page.BREAK }
    override val rotateForBreak by c.setting("Rotate For Break", true, "Rotate towards block while breaking") { vis() && page == Page.BREAK }

    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks") { vis() && page == Page.BREAK }

    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation") { vis() && page == Page.PLACE }

    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() && page == Page.PLACE }
}
