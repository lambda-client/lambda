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
                while (x != 0.0 && world.getCollisionBoxes(player, player.entityBoundingBox.offset(x, (-player.stepHeight).toDouble(), 0.0)).isEmpty()) {
                    if (x < 0.05 && x >= -0.05) {
                        x = 0.0
                    } else if (x > 0.0) {
                        x -= 0.05
                    } else {
                        x += 0.05
                    }
                }
                while (z != 0.0 && world.getCollisionBoxes(player, player.entityBoundingBox.offset(0.0, (-player.stepHeight).toDouble(), z)).isEmpty()) {
                    if (z < 0.05 && z >= -0.05) {
                        z = 0.0
                    } else if (z > 0.0) {
                        z -= 0.05
                    } else {
                        z += 0.05
                    }
                }
                while (x != 0.0 && z != 0.0 && world.getCollisionBoxes(player, player.entityBoundingBox.offset(x, (-player.stepHeight).toDouble(), z)).isEmpty()) {
                    if (x < 0.05 && x >= -0.05) {
                        x = 0.0
                    } else if (x > 0.0) {
                        x -= 0.05
                    } else {
                        x += 0.05
                    }
                    if (z < 0.05 && z >= -0.05) {
                        z = 0.0
                    } else if (z > 0.0) {
                        z -= 0.05
                    } else {
                        z += 0.05
                    }
                }
                event.x = x
                event.z = z
            }
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