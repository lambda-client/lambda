
package com.minato.event.events

import com.minato.event.Event
import com.minato.interaction.managers.rotating.RotationRequest
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
