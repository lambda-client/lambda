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
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.rotating.RotationConfig
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.matches

data class PlaceRequest(
    val contexts: Collection<PlaceContext>,
    val build: BuildConfig,
    val rotation: RotationConfig,
    val hotbar: HotbarConfig,
    val pendingInteractions: MutableCollection<BuildContext>,
    val onPlace: () -> Unit
) : Request() {
    override val config = build.placing
    override val done: Boolean
        get() = runSafe {
            contexts.all { it.expectedState.matches(blockState(it.blockPos)) }
        } == true
}
