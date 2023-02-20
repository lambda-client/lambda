package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.RotationUtils.getRotationToEntity
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent

class UseThrowableOnEntity(
    private val targetEntity: Entity,
    private val amount: Int = 1,
    private val useHand: EnumHand = EnumHand.MAIN_HAND,
    override var rotation: Vec2f? = null,
) : RotatingActivity, Activity() {
    private var used = 0

    override fun SafeClientEvent.onInitialize() {
        rotation = getRotation()
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            rotation = getRotation()

            connection.sendPacket(CPacketPlayerTryUseItem(useHand))

            used++

            if (used == amount) success()
        }
    }

    private fun SafeClientEvent.getRotation() = if (targetEntity == player) {
        Vec2f(player.rotationYaw, 90f)
    } else {
        getRotationToEntity(targetEntity)
    }
}