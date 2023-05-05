package com.lambda.client.activity.activities.construction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.AttachItemFrame
import com.lambda.client.activity.activities.interaction.AttachMap
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.world.isReplaceable
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.util.EnumFacing

class Graffiti : Activity() {
    override fun SafeClientEvent.onInitialize() {
        VectorUtils.getBlockPosInSphere(player.positionVector, 5.0f)
            .filter { world.getBlockState(it).isReplaceable }
            .forEach {
                addSubActivities(
                    AttachItemFrame(player.heldItemMainhand, it, player.horizontalFacing.opposite)
                )
            }

        world.loadedEntityList
            .filterIsInstance<EntityItemFrame>()
            .forEach {
                addSubActivities(
                    AttachMap(it, player.heldItemMainhand)
                )
            }
    }
}