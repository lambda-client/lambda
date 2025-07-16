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

import com.lambda.config.groups.InteractionConfig.InteractConfirmationMode
import com.lambda.interaction.request.RequestConfig
import com.lambda.interaction.request.interacting.InteractionManager
import com.lambda.interaction.request.interacting.InteractionRequest

abstract class InteractConfig : RequestConfig<InteractionRequest>() {
    abstract val rotate: Boolean
    abstract val swingHand: Boolean
    abstract val interactSwingType: BuildConfig.SwingType
    abstract val interactConfirmationMode: InteractConfirmationMode

    override fun requestInternal(request: InteractionRequest, queueIfClosed: Boolean) {
        InteractionManager.request(request, queueIfClosed)
    }
}