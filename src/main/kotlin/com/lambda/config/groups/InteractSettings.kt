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
import com.lambda.event.events.TickEvent
import com.lambda.interaction.request.interacting.InteractConfig
import com.lambda.util.NamedEnum

class InteractSettings(
    c: Configurable,
    groupPath: List<NamedEnum> = emptyList(),
    vis: () -> Boolean = { true }
) : InteractConfig {
    override val rotate by c.setting("Rotate For Interact", true, "Rotates the player to look at the block when interacting", visibility = vis).group(groupPath)
    override val swingHand by c.setting("Swing On Interact", true, "Swings the players hand after interacting", visibility = vis).group(groupPath)
    override val interactStageMask by c.setting("Interact Stage Mask", setOf(TickEvent.Input.Post, TickEvent.Player.Post), description = "The sub-tick timing at which interact actions are performed", visibility = vis).group(groupPath)
    override val interactSwingType by c.setting("Interact Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { vis() && swingHand }.group(groupPath)
    override val interactConfirmationMode by c.setting("Interact Confirmation Mode", InteractionConfig.InteractConfirmationMode.InteractThenAwait, "The style of confirmation for interactions", visibility = vis).group(groupPath)
}