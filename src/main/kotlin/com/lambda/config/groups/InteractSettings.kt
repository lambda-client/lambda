/*
 * Copyright 2026 Lambda
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
import com.lambda.interaction.managers.interacting.InteractConfig
import com.lambda.interaction.managers.interacting.InteractConfig.AirPlaceMode
import com.lambda.interaction.managers.interacting.InteractConfig.InteractConfirmationMode
import com.lambda.util.NamedEnum

class InteractSettings(
    c: Configurable,
    vararg baseGroup: NamedEnum,
    prefix: String = "",
    override val visibility: () -> Boolean = { true },
) : SettingGroup(c), InteractConfig {
    override val rotate by c.setting("${prefix}Rotate For Interact", true, "Rotate towards block while placing", visibility = visibility).group(*baseGroup).index()
    override val airPlace by c.setting("${prefix}Air Place", AirPlaceMode.Grim, "Allows for placing blocks without adjacent faces", visibility = visibility).group(*baseGroup).index()
    override val axisRotateSetting by c.setting("${prefix}Axis Rotate", true, "Overrides the Rotate For Place setting and rotates the player on each axis to air place rotational blocks") { visibility() && airPlace.isEnabled }.group(*baseGroup).index()
    override val sorter by c.setting("${prefix}Interaction Sorter", ActionConfig.SortMode.Tool, "The order in which placements are performed", visibility = visibility).group(*baseGroup).index()
    override val tickStageMask by c.setting("${prefix}Interaction Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which place actions are performed", displayClassName = true, visibility = visibility).group(*baseGroup).index()
    override val interactConfirmationMode by c.setting("${prefix}Interact Confirmation", InteractConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation", visibility = visibility).group(*baseGroup).index()
    override val interactDelay by c.setting("${prefix}Interact Delay", 0, 0..3, 1, "Tick delay between interacting with another block", visibility = visibility).group(*baseGroup).index()
    override val interactionsPerTick by c.setting("${prefix}Interactions Per Tick", 9, 1..30, 1, "Maximum instant block places per tick", visibility = visibility).group(*baseGroup).index()
    override val swing by c.setting("${prefix}Swing On Interact", true, "Swings the players hand when placing", visibility = visibility).group(*baseGroup).index()
    override val swingType by c.setting("${prefix}Interact Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { visibility() && swing }.group(*baseGroup).index()
    override val sounds by c.setting("${prefix}Place Sounds", true, "Plays the placing sounds", visibility = visibility).group(*baseGroup).index()
}