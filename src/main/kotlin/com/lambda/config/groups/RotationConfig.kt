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

import com.lambda.config.ISettingGroup
import com.lambda.config.Setting
import com.lambda.event.events.TickEvent
import com.lambda.interaction.managers.rotating.Rotation.Companion.dist
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import com.lambda.util.math.times
import com.lambda.util.player.CheckedHit
import kotlin.collections.mapNotNull
import kotlin.collections.minByOrNull

interface RotationConfig : ISettingGroup {
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

    open class Instant(mode: RotationMode, override val visibility: () -> Boolean = { true }) : RotationConfig {
	    override val settings = mutableListOf<Setting<*, *>>()
        override val rotationMode = mode
        override val keepTicks = 1
        override val decayTicks = 1
        override val turnSpeed = 180.0
        override val tickStageMask = RotationManager.openStages.toSet()
    }

    @Suppress("unused")
    enum class PointSelection(
        override val displayName: String,
        override val description: String,
        val select: (Collection<CheckedHit>) -> CheckedHit?
    ) : NamedEnum, Describable {
        ByRotation(
            "By Rotation",
            "Choose the point that needs the least rotation from your current view (minimal camera turn).",
            select = { hits ->
                hits.minByOrNull { RotationManager.activeRotation dist it.rotation }
            }
        ),
        Optimum(
            "Optimum",
            "Choose the point closest to the average of all candidates (balanced and stable aim).",
            select = { hits ->
                val optimum = hits
                    .mapNotNull { it.hit.pos }
                    .reduceOrNull { acc, pos -> acc.add(pos) }
                    ?.times(1 / hits.size.toDouble())

                optimum?.let { center ->
                    hits.minByOrNull { it.hit.pos?.distSq(center) ?: 0.0 }
                }
            }
        )
    }
}