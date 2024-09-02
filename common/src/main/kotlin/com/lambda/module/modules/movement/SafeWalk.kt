package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.entity.LivingEntity

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you at the edge",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.GRIM)
) {
    private val sneakOnLedge by setting("Sneak On Ledge", true)
    private val ledgeDistance by setting("Ledge Distance", 0.2, 0.0..0.5, 0.01, unit = " blocks")
    private val stepHeight by setting("Step Height", 1.1, 0.0..4.0, 0.05, unit = " blocks")

    init {
        listener<MovementEvent.InputUpdate> {
            if (sneakOnLedge && player.isOnGround && player.isNearLedge(ledgeDistance, stepHeight)) {
                it.input.sneaking = true
            }
        }

        listener<MovementEvent.ClipAtLedge> {
            if (!sneakOnLedge) it.clip = true
        }
    }

    private fun LivingEntity.isNearLedge(distance: Double, stepHeight: Double): Boolean {
        fun checkDirection(deltaX: Double, deltaZ: Double): Boolean {
            var dx = deltaX
            var dz = deltaZ
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