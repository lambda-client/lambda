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
import com.lambda.config.groups.InteractionConfig
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState

data class PlaceRequest(
    val placeContext: PlaceContext,
    val buildConfig: BuildConfig,
    val rotationConfig: RotationConfig,
    val hotbarConfig: HotbarConfig,
    val interactionConfig: InteractionConfig,
    val pendingInteractionsList: MutableCollection<BuildContext>,
    val prio: Priority = 0,
    val onPlace: () -> Unit
) : Request(prio) {
    override val done: Boolean
        get() = runSafe {
            placeContext.targetState.matches(blockState(placeContext.expectedPos), placeContext.expectedPos, world)
        } == true
}