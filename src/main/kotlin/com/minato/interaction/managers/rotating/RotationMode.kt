
package com.minato.interaction.managers.rotating

import com.minato.util.Describable
import com.minato.util.NamedEnum

/**
 * @property Silent Spoofing server-side rotation.
 * @property Sync Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
 * @property Lock Locks the camera client-side.
 */
enum class RotationMode(
    override val displayName: String,
    override val description: String
) : NamedEnum, Describable {
    Silent("Silent", "Rotate for interactions without moving your camera (server-only rotation spoof)."),
    Sync("Sync", "Rotate both server and client view so your camera turns to face the target."),
    Lock("Lock", "Keep rotation fixed on the target until the action finishes; ignores other rotation changes.")
}
