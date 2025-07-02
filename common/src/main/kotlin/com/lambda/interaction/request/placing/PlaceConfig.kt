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
import com.lambda.interaction.request.RequestConfig

abstract class PlaceConfig(
    priority: Int
) : RequestConfig<PlaceRequest>(priority) {
    abstract val rotateForPlace: Boolean
    abstract val airPlace: AirPlaceMode
    protected abstract val axisRotateSetting: Boolean
    val axisRotate
        get() = rotateForPlace && airPlace.isEnabled() && axisRotateSetting
    abstract val placeStageMask: Set<Event>
    abstract val placeConfirmationMode: PlaceConfirmationMode
    abstract val maxPendingPlacements: Int
    abstract val placementsPerTick: Int
    abstract val swing: Boolean
    abstract val swingType: BuildConfig.SwingType
    abstract val sounds: Boolean

    override fun requestInternal(request: PlaceRequest, queueIfClosed: Boolean) {
        PlaceManager.request(request, queueIfClosed)
    }

    enum class AirPlaceMode {
        None,
        Standard,
        Grim;

        fun isEnabled() = this != None
    }

    enum class PlaceConfirmationMode {
        None,
        PlaceThenAwait,
        AwaitThenPlace
    }
}
