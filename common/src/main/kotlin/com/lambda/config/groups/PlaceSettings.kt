/*
 * Copyright 2025 Lambda
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
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.placing.PlaceConfig

class PlaceSettings(
    c: Configurable,
    priority: Priority = 0,
    vis: () -> Boolean = { true }
) : PlaceConfig(priority) {
    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() }
    override val placeConfirmationMode by c.setting("Place Confirmation", PlaceConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation") { vis() }
    override val maxPendingPlacements by c.setting("Max Pending Placements", 2, 0..5, 1, "The maximum amount of pending placements") { vis() }
    override val placementsPerTick by c.setting("Instant Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick") { vis() }
    override val swing by c.setting("Swing", true, "Swings the players hand when placing") { vis() }
    override val swingType by c.setting("Place Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { vis() }
    override val sounds by c.setting("Place Sounds", true, "Plays the placing sounds") { vis() }
}