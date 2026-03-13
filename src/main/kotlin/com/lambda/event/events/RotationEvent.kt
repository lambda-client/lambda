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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.interaction.managers.rotating.RotationRequest
import net.minecraft.client.input.Input

sealed class RotationEvent {

    /**
     * This event allows listeners to modify the yaw relative to which the movement input is going to be constructed
     *
     * @property strafeYaw The angle at which the player will move when pressing W
     * Changing this value will never force the anti cheat to flag you because RotationManager is designed to modify the key input instead
     */
    data class StrafeInput(var strafeYaw: Double, val input: Input) : Event

    data class Post(val request: RotationRequest) : Event
}
