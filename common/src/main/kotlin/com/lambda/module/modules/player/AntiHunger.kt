package com.lambda.module.modules.player

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

object AntiHunger : Module(
    name = "AntiHunger",
    description = "Reduces food consumption",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val cancelMovementState by setting("Cancel Movement State", true)
    
    init {
        listener<PacketEvent.Send.Pre> {
            when (it.packet) {
                is ClientCommandC2SPacket -> {
                    if (cancelMovementState &&
                        (it.packet.mode == ClientCommandC2SPacket.Mode.START_SPRINTING ||
                                it.packet.mode == ClientCommandC2SPacket.Mode.STOP_SPRINTING)) {
                        it.cancel()
                    }
                }
                
                is PlayerMoveC2SPacket -> {
                    it.packet.onGround = (player.fallDistance <= 0 || interaction.isBreakingBlock) && player.isFallFlying
                }
            }
        }
    }
}