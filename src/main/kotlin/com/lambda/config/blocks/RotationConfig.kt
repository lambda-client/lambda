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

package com.lambda.config.blocks

import com.lambda.event.events.TickEvent
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.managers.rotating.RotationMode

interface RotationConfig {
    /**
     * - [RotationMode.Silent] Spoofing server-side rotation.
     * - [RotationMode.Sync] Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
     * - [RotationMode.Lock] Locks the camera client-side.
     */
    val rotationMode: RotationMode

    /**
     * The fastest the rotation may turn, in degrees per tick.
     */
    val turnSpeed: Double

    /**
     * The most the turning speed may change from one tick to the next, in degrees per tick.
     */
    val turnAcceleration: Double

    /**
     * The standard deviation of the turning speed, in degrees per tick.
     */
    val turnNoise: Double

    /**
     * Ticks the rotation should not be changed.
     */
    val keepTicks: Int

    /**
     * Ticks to rotate back to the actual rotation.
     */
    val decayTicks: Int

    val tickStageMask: Set<TickEvent>

    open class Instant(mode: RotationMode) : RotationConfig {
        override val rotationMode = mode
        override val keepTicks = 1
        override val decayTicks = 1
        override val turnSpeed = 180.0
        override val turnAcceleration = 360.0
        override val turnNoise = 0.0
        override val tickStageMask = RotationManager.openStages.toSet()
    }
}