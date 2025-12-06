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
import com.lambda.config.SettingGroup
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.request.interacting.InteractConfig
import com.lambda.interaction.request.interacting.InteractConfig.AirPlaceMode
import com.lambda.interaction.request.interacting.InteractConfig.PlaceConfirmationMode
import com.lambda.util.NamedEnum

class InteractSettings(
    c: Configurable,
    baseGroup: NamedEnum
) : SettingGroup(c), InteractConfig {
    override val rotate by c.setting("Rotate For Interact", true, "Rotate towards block while placing").group(baseGroup).index()
    override val airPlace by c.setting("Air Place", AirPlaceMode.None, "Allows for placing blocks without adjacent faces").group(baseGroup).index()
    override val axisRotateSetting by c.setting("Axis Rotate", true, "Overrides the Rotate For Place setting and rotates the player on each axis to air place rotational blocks") { airPlace.isEnabled }.group(baseGroup).index()
    override val sorter by c.setting("Interaction Sorter", ActionConfig.SortMode.Tool, "The order in which placements are performed").group(baseGroup).index()
    override val tickStageMask by c.setting("Interaction Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), description = "The sub-tick timing at which place actions are performed").group(baseGroup).index()
    override val interactConfirmationMode by c.setting("Interact Confirmation", PlaceConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation").group(baseGroup).index()
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 5, 0..30, 1, "The maximum amount of pending placements").group(baseGroup).index()
    override val interactionsPerTick by c.setting("Interactions Per Tick", 1, 1..30, 1, "Maximum instant block places per tick").group(baseGroup).index()
    override val swing by c.setting("Swing On Interact", true, "Swings the players hand when placing").group(baseGroup).index()
    override val swingType by c.setting("Interact Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { swing }.group(baseGroup).index()
    override val sounds by c.setting("Place Sounds", true, "Plays the placing sounds").group(baseGroup).index()
}