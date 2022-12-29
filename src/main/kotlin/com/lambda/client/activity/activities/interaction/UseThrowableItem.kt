package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.RotatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.network.play.server.SPacketOpenWindow
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent

class UseThrowableItem(
    override var rotation: Vec2f = Vec2f.ZERO,
    ) : RotatingActivity, Activity() {

    override fun SafeClientEvent.onInitialize() {
        rotation = Vec2f(player.rotationYaw, 90f)
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.END) {
                connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))
                activityStatus = ActivityStatus.SUCCESS
            }
        }
    }

}