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

import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestConfig

abstract class PlaceConfig(
    priority: Priority
) : RequestConfig<PlaceRequest>(priority) {
    abstract val rotateForPlace: Boolean
    abstract val placeConfirmation: PlaceConfirmation
    abstract val placementsPerTick: Int

    override fun requestInternal(request: PlaceRequest) {
        PlaceManager.registerRequest(this, request)
    }

    enum class PlaceConfirmation {
        None,
        PlaceThenAwait,
        AwaitThenPlace
    }
}