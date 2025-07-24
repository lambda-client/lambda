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

package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.MovementUtils.motionX
import com.lambda.util.player.MovementUtils.motionZ
import com.lambda.util.player.MovementUtils.sneaking
import net.minecraft.entity.LivingEntity

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you at the edge",
    tag = ModuleTag.MOVEMENT,
) {
    private val sneakOnLedge by setting("Sneak On Ledge", true)
    private val ledgeDistance by setting("Ledge Distance", 0.2, 0.0..0.5, 0.01, unit = " blocks")
    private val stepHeight by setting("Step Height", 1.1, 0.0..4.0, 0.05, unit = " blocks")

    init {
        listen<MovementEvent.InputUpdate> {
            if (sneakOnLedge && player.isOnGround && player.isNearLedge(ledgeDistance, stepHeight))
                it.input.sneaking = true
        }

        listen<MovementEvent.ClipAtLedge> {
            if (!sneakOnLedge) it.clip = true
        }
    }

    fun LivingEntity.isNearLedge(distance: Double, stepHeight: Double): Boolean {
        fun checkDirection(deltaX: Double, deltaZ: Double): Boolean {
            var dx = deltaX + motionX
            var dz = deltaZ + motionZ
            while (dx != 0.0 || dz != 0.0) {
                if (world.isBlockSpaceEmpty(this, boundingBox.offset(dx, -stepHeight, dz))) {
                    return true
                }
                if (dx != 0.0) dx = adjustDelta(dx)
                if (dz != 0.0) dz = adjustDelta(dz)
            }
            return false
        }

        return checkDirection(distance, 0.0) ||  // Positive X
                checkDirection(-distance, 0.0) || // Negative X
                checkDirection(0.0, distance) ||  // Positive Z
                checkDirection(0.0, -distance) || // Negative Z
                checkDirection(distance, distance) || // Positive X, Positive Z
                checkDirection(-distance, distance) || // Negative X, Positive Z
                checkDirection(distance, -distance) || // Positive X, Negative Z
                checkDirection(-distance, -distance) // Negative X, Negative Z
    }

    private fun adjustDelta(delta: Double) =
        when {
            delta < 0.05 && delta >= -0.05 -> 0.0
            delta > 0.0 -> delta - 0.05
            else -> delta + 0.05
        }
}
