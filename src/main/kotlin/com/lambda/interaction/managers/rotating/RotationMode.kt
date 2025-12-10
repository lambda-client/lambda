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

import com.lambda.util.Describable
import com.lambda.util.NamedEnum

/**
 * @property Silent Spoofing server-side rotation.
 * @property Sync Spoofing server-side rotation and adjusting client-side movement based on reported rotation (for Grim).
 * @property Lock Locks the camera client-side.
 * @property None No rotation.
 */
enum class RotationMode(
    override val displayName: String,
    override val description: String
) : NamedEnum, Describable {
    Silent("Silent", "Rotate for interactions without moving your camera (server-only rotation spoof)."),
    Sync("Sync", "Rotate both server and client view so your camera turns to face the target."),
    Lock("Lock", "Keep rotation fixed on the target until the action finishes; ignores other rotation changes."),
    None("None", "Do not auto-rotate; use your current view direction.")
}
