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

package com.lambda.interaction.managers.rotating

import com.lambda.config.ISettingGroup
import com.lambda.config.Setting
import com.lambda.event.events.TickEvent

interface RotationConfig : ISettingGroup {
    /**
     * - [RotationMode.Silent] Spoofing server-side rotation.
     * - [RotationMode.Sync] Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
     * - [RotationMode.Lock] Locks the camera client-side.
     * - [RotationMode.None] No rotation.
     */
    val rotationMode: RotationMode

    /**
     * The rotation speed (in degrees).
     */
    val turnSpeed: Double

    /**
     * Ticks the rotation should not be changed.
     */
    val keepTicks: Int

    /**
     * Ticks to rotate back to the actual rotation.
     */
    val decayTicks: Int

    val tickStageMask: Set<TickEvent>

    val rotate: Boolean get() = rotationMode != RotationMode.None

    open class Instant(mode: RotationMode) : RotationConfig {
	    override val settings = mutableListOf<Setting<*, *>>()
        override val rotationMode = mode
        override val keepTicks = 1
        override val decayTicks = 1
        override val turnSpeed = 180.0
        override val tickStageMask = RotationManager.openStages.toSet()
    }
}
