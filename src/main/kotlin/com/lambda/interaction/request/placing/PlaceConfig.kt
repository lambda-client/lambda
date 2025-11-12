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

package com.lambda.interaction.request.placing

import com.lambda.config.groups.BuildConfig
import com.lambda.event.Event
import com.lambda.util.Describable
import com.lambda.util.NamedEnum

interface PlaceConfig {
    val rotateForPlace: Boolean
    val airPlace: AirPlaceMode
    val axisRotateSetting: Boolean
    val axisRotate
        get() = rotateForPlace && airPlace.isEnabled && axisRotateSetting
    val tickStageMask: Set<Event>
    val placeConfirmationMode: PlaceConfirmationMode
    val maxPendingPlacements: Int
    val placementsPerTick: Int
    val swing: Boolean
    val swingType: BuildConfig.SwingType
    val sounds: Boolean

    enum class AirPlaceMode(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        None("None", "Do not attempt air placements; only place against valid supports."),
        Standard("Standard", "Try common air-place techniques for convenience; moderate compatibility."),
        Grim("Grim", "Use grim specific air placing.")
        ;

        val isEnabled get() = this != None
    }

    enum class PlaceConfirmationMode(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        None("No confirmation", "Place immediately without waiting for the server; lowest latency, possible brief desync."),
        PlaceThenAwait("Place now, confirm later", "Show placement right away, then wait for server confirmation to verify."),
        AwaitThenPlace("Confirm first, then place", "Wait for server response before showing placement; most accurate, adds a short delay.")
    }
}
