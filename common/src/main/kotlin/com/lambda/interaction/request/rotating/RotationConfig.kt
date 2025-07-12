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

package com.lambda.interaction.request.rotating

import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestConfig

/**
 * Abstract base class for configuring rotation behavior.
 *
 * @param priority The priority of this configuration.
 */
abstract class RotationConfig(priority: Priority) : RequestConfig<RotationRequest>(priority) {
    /**
     * - [RotationMode.Silent] Spoofing server-side rotation.
     * - [RotationMode.Sync] Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
     * - [RotationMode.Lock] Locks the camera client-side.
     * - [RotationMode.None] No rotation.
     */
    abstract val rotationMode: RotationMode

    /**
     * The rotation speed (in degrees).
     */
    abstract val turnSpeed: Double

    /**
     * Ticks the rotation should not be changed.
     */
    abstract val keepTicks: Int

    /**
     * Ticks to rotate back to the actual rotation.
     */
    abstract val decayTicks: Int

    val rotate: Boolean get() = rotationMode != RotationMode.None

    override fun requestInternal(request: RotationRequest, queueIfClosed: Boolean) {
        RotationManager.request(request, queueIfClosed)
    }

    open class Instant(mode: RotationMode, priority: Priority = 0) : RotationConfig(priority) {
        override val turnSpeed get() = 360.0
        override val keepTicks get() = 1
        override val decayTicks get() = 1
        override val rotationMode = mode
    }
}
