package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.playerIsOnGround
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer

/**
 * Movement taken from Seppuku
 * https://github.com/seppukudevelopment/seppuku/blob/005e2da/src/main/java/me/rigamortis/seppuku/impl/module/player/NoHungerModule.java
 */
object AntiHunger : Module(
    name = "AntiHunger",
    description = "Reduces hunger lost from moving around",
    category = Category.MOVEMENT
) {
    private val cancelMovementState by setting("Cancel Movement State", true)

    init {
        safeListener<PacketEvent.Send> {
            when (it.packet) {
                is CPacketEntityAction -> {
                    if (cancelMovementState &&
                        (it.packet.action == CPacketEntityAction.Action.START_SPRINTING ||
                            it.packet.action == CPacketEntityAction.Action.STOP_SPRINTING)) {
                        it.cancel()
                    }
                }
                is CPacketPlayer -> {
                    it.packet.playerIsOnGround = (player.fallDistance <= 0 || mc.playerController.isHittingBlock) && player.isElytraFlying
                }
            }
        }
    }
}
