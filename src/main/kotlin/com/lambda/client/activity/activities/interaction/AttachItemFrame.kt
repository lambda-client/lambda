package com.lambda.client.activity.activities.interaction

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.ItemInfo
import com.lambda.client.activity.types.RotatingActivity
import com.lambda.client.activity.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import kotlinx.coroutines.launch
import net.minecraft.init.Items
import net.minecraft.network.play.server.SPacketSpawnObject
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

class AttachItemFrame(
    private val placePos: BlockPos,
    private val facing: EnumFacing,
    override var rotation: Vec2f? = null,
    override var timeout: Long = 1000L
) : RotatingActivity, TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        rotation = getRotationTo(getHitVec(placePos, facing))

        addSubActivities(
            AcquireItemInActiveHand(ItemInfo(Items.ITEM_FRAME))
        )
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketSpawnObject) return@safeListener
            if (it.packet.type != 71) return@safeListener

            defaultScope.launch {
                onMainThreadSafe {
                    success()
                }
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is AcquireItemInActiveHand) return

        LambdaMod.LOG.info(playerController.processRightClickBlock(
            player,
            world,
            placePos,
            facing,
            getHitVec(placePos, facing),
            EnumHand.MAIN_HAND
        ))
    }
}