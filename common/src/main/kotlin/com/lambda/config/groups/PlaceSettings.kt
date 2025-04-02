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
    override val airPlace by c.setting("Air Place", AirPlaceMode.None, "Allows for placing blocks without adjacent faces") { vis() }
    override val axisRotateSetting by c.setting("Axis Rotate", true, "Overrides the Rotate For Place setting and rotates the player on each axis to air place rotational blocks") { vis() && airPlace.isEnabled() }
    override val placeConfirmationMode by c.setting("Place Confirmation", PlaceConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation") { vis() }
    override val maxPendingPlacements by c.setting("Max Pending Placements", 5, 0..30, 1, "The maximum amount of pending placements") { vis() }
    override val placementsPerTick by c.setting("Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick") { vis() }
    override val swing by c.setting("Swing", true, "Swings the players hand when placing") { vis() }
    override val swingType by c.setting("Place Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { vis() && swing }
    override val sounds by c.setting("Place Sounds", true, "Plays the placing sounds") { vis() }
}