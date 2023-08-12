package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.types.StackSelection
import com.lambda.client.activity.types.RotatingActivity
import com.lambda.client.activity.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.math.RotationUtils.getRotationToEntity
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.launch
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.SPacketSpawnObject
import net.minecraft.util.EnumHand

class AttachMap(
    private val itemFrame: EntityItemFrame,
    private val mapID: Int,
    override var rotation: Vec2f? = null,
    override var timeout: Long = 1000L
) : RotatingActivity, TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        rotation = getRotationToEntity(itemFrame)

        addSubActivities(
            AcquireItemInActiveHand(StackSelection().apply { selection = isItem(Items.MAP) and hasDamage(mapID) })
        )
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketSpawnObject) return@safeListener
            if (it.packet.entityID != itemFrame.entityId) return@safeListener

            defaultScope.launch {
                onMainThreadSafe {
                    success()
                }
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is AcquireItemInActiveHand) return

        connection.sendPacket(CPacketUseEntity(itemFrame, EnumHand.MAIN_HAND))
    }
}