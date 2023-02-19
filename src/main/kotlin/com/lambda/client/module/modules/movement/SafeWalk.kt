package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.Scaffold
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you from walking off edges",
    category = Category.MOVEMENT,
    alwaysListening = true
) {
    private val checkFallDist by setting("Safe Fall Allowed", false, description = "Check fall distance from edge")

    init {
        safeListener<PlayerMoveEvent> { event ->
            if ((isEnabled || (Scaffold.isEnabled && Scaffold.safeWalk))
                && player.onGround
                && !BaritoneUtils.isPathing
                && if (checkFallDist) !isEdgeSafe else true) {
                /**
                 * Code here is from net.minecraft.Entity::move
                 * Cannot do a mixin on this method's sneak section due to mixin compatibility issues with Future (and possibly other clients)
                 */

                var x = event.x
                var z = event.z

                var boundingBox = player.entityBoundingBox.offset(0.0, -.6, 0.0)

                while (x != 0.0 && world.getCollisionBoxes(player, boundingBox.offset(x, 0.0, 0.0)).isEmpty()) {
                    x = updateCoordinate(x)
                }

                boundingBox = boundingBox.offset(x, 0.0, 0.0)

                while (z != 0.0 && world.getCollisionBoxes(player, boundingBox.offset(0.0, 0.0, z)).isEmpty()) {
                    z = updateCoordinate(z)
                }

                event.x = x
                event.z = z
            }
        }
    }

    private fun updateCoordinate(coordinate: Double): Double {
        return if (coordinate < 0.05 && coordinate >= -0.05) {
            0.0
        } else if (coordinate > 0.0) {
            coordinate - 0.05
        } else {
            coordinate + 0.05
        }
    }


    private val isEdgeSafe: Boolean
        get() = runSafeR {
            val pos = player.flooredPosition.toVec3d(0.5, 0.0, 0.5)
            world.rayTraceBlocks(
                pos,
                pos.subtract(0.0, 3.1, 0.0),
                true,
                true,
                false
            ) != null
        } ?: false
}