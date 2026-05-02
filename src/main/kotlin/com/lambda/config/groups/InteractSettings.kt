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

import com.lambda.config.Config
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.config.groups.InteractConfig.AirPlaceMode
import com.lambda.config.groups.InteractConfig.InteractConfirmationMode

class InteractSettings(override val c: Config) : InteractConfig {
    override val rotate by c.setting("Rotate For Interact", true, "Rotate towards block while placing")
    override val airPlace by c.setting("Air Place", AirPlaceMode.Grim, "Allows for placing blocks without adjacent faces")
    override val axisRotateSetting by c.setting("Axis Rotate", true, "Overrides the Rotate For Place setting and rotates the player on each axis to air place rotational blocks") { airPlace.isEnabled }
    override val sorter by c.setting("Interaction Sorter", ActionConfig.SortMode.Tool, "The order in which placements are performed")
    override val tickStageMask by c.setting("Interaction Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which place actions are performed", displayClassName = true)
    override val interactConfirmationMode by c.setting("Interact Confirmation", InteractConfirmationMode.PlaceThenAwait, "Wait for block placement confirmation")
    override val interactDelay by c.setting("Interact Delay", 0, 0..3, 1, "Tick delay between interacting with another block")
    override val interactionsPerTick by c.setting("Interactions Per Tick", 9, 1..30, 1, "Maximum instant block places per tick")
    override val swing by c.setting("Swing On Interact", true, "Swings the players hand when placing")
    override val swingType by c.setting("Interact Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { swing }
    override val sounds by c.setting("Place Sounds", true, "Plays the placing sounds")
}