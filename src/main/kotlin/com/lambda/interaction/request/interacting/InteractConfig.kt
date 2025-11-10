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

package com.lambda.interaction.request.interacting

import com.lambda.config.groups.BuildConfig
import com.lambda.event.Event
import com.lambda.interaction.request.RequestConfig
import com.lambda.util.Describable
import com.lambda.util.NamedEnum

interface InteractConfig : RequestConfig {
    val rotate: Boolean
    val swingHand: Boolean
    val tickStageMask: Set<Event>
    val interactSwingType: BuildConfig.SwingType
    val interactConfirmationMode: InteractConfirmationMode

    enum class InteractConfirmationMode(
        override val displayName: String,
        override val description: String
    ): NamedEnum, Describable {
        None("No confirmation", "Send the interaction and don’t wait for the server. Lowest latency, but effects may briefly appear if the server rejects it."),
        InteractThenAwait("Interact now, confirm later", "Show interaction effects immediately, then wait for the server to confirm. Feels instant while still verifying the result."),
        AwaitThenInteract("Confirm first, then interact", "Wait for the server response before showing any effects. Most accurate and safe, but adds a short delay.")
    }
}