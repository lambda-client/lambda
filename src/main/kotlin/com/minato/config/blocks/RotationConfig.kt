
package com.minato.config.blocks

import com.minato.event.events.TickEvent
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.interaction.managers.rotating.RotationMode

interface RotationConfig {
    /**
     * - [RotationMode.Silent] Spoofing server-side rotation.
     * - [RotationMode.Sync] Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
     * - [RotationMode.Lock] Locks the camera client-side.
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

    open class Instant(mode: RotationMode) : RotationConfig {
        override val rotationMode = mode
        override val keepTicks = 1
        override val decayTicks = 1
        override val turnSpeed = 180.0
        override val tickStageMask = RotationManager.openStages.toSet()
    }
}