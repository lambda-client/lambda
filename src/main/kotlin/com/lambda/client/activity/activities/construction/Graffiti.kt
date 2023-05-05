package com.lambda.client.activity.activities.construction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.AttachItemFrame
import com.lambda.client.activity.activities.interaction.AttachMap
import com.lambda.client.activity.types.LoopWhileActivity
import com.lambda.client.activity.types.RepeatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.misc.Graffiti
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.isReplaceable
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.util.EnumFacing

class Graffiti(
    private val mapID: Int,
    override val loopWhile: SafeClientEvent.() -> Boolean = { Graffiti.isEnabled },
    override var currentLoops: Int = 0
) : LoopWhileActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val range = 4.95f

        // 1. Check for missing maps in item frames
        world.loadedEntityList
            .filterIsInstance<EntityItemFrame>()
            .minByOrNull { player.distanceTo(it.positionVector) }
            ?.let {
                if (player.distanceTo(it.positionVector) > range) return

                addSubActivities(
                    AttachMap(it, mapID)
                )
                return
            }

        // 2. Check for surface to place item frames on and place it
        VectorUtils.getBlockPosInSphere(player.positionVector, range)
            .mapNotNull { getNeighbour(it, 1, range, true, EnumFacing.HORIZONTALS) }
            .minByOrNull { player.distanceTo(it.hitVec) }
            ?.let {
                if (player.distanceTo(it.hitVec) > range) return

                addSubActivities(
                    AttachItemFrame(it.pos, it.side)
                )
            }
    }
}