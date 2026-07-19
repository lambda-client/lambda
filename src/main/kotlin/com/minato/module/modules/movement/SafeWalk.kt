
package com.minato.module.modules.movement

import com.minato.event.events.MovementEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.player.MovementUtils.motionX
import com.minato.util.player.MovementUtils.motionZ
import com.minato.util.player.MovementUtils.sneaking
import net.minecraft.entity.LivingEntity

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you at the edge",
    tag = ModuleTag.MOVEMENT,
) {
    private val sneakOnLedge by setting("Sneak On Ledge", true)
    private val ledgeDistance by setting("Ledge Distance", 0.2, 0.0..0.5, 0.01, unit = " blocks")
    private val stepHeight by setting("Step Height", 0.5, 0.0..4.0, 0.05, unit = " blocks")

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
