package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.SafewalkEvent
import com.lambda.mixin.entity.MixinEntity
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener

/**
 * @see MixinEntity.move_isSneaking
 */
object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you from walking off edges",
    category = Category.MOVEMENT
) {
    private val checkFallDist by setting("Check Fall Distance", true, description = "Check fall distance from edge")

    init {

        safeListener<SafewalkEvent> {

            BaritoneUtils.settings?.assumeSafeWalk?.value = isEdgeSafe || !checkFallDist
            it.sneak = isEdgeSafe || !checkFallDist

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