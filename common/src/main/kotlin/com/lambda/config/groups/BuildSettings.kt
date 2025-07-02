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
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.placing.PlaceConfig

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    enum class Page {
        General, Break, Place
    }

    private val page by c.setting("Build Page", Page.General, "Current page", vis)

    // General
    override val pathing by c.setting("Pathing", true, "Path to blocks") { vis() && page == Page.General }
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks") { vis() && page == Page.General && pathing }
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks") { vis() && page == Page.General }
    override val interactionsPerTick by c.setting("Interactions Per Tick", 5, 1..30, 1, "The amount of interactions that can happen per tick", visibility = vis)
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 20, 1..30, 1, "Dont wait for this many interactions for the server response") { vis() && page == Page.General }

    // Breaking
    override val breaking = BreakSettings(c) { page == Page.Break && vis() }

    // Placing
    override val placing = PlaceSettings(c) { page == Page.Place && vis() }

    override val interactionTimeout by c.setting("Interaction Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks") { vis() && ((page == Page.Place && placing.placeConfirmationMode != PlaceConfig.PlaceConfirmationMode.None) || (page == Page.Break && breaking.breakConfirmation != BreakConfirmationMode.None)) }
}