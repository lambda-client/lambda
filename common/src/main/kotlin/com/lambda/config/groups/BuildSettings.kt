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
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 1, 1..10, 1, "Dont wait for this many interactions for the server response") { vis() && page == Page.General }

    // Breaking
    override val breakSettings = BreakSettings(c) { page == Page.Break && vis() }

    // Placing
    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() && page == Page.Place }
    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation") { vis() && page == Page.Place }
    override val placementsPerTick by c.setting("Instant Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick") { vis() && page == Page.Place }

    override val interactionTimeout by c.setting("Interaction Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks") { vis() && (page == Page.Place && placeConfirmation || page == Page.Break && breakSettings.breakConfirmation != BreakConfirmationMode.None) }
}
