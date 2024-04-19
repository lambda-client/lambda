package com.lambda.module.modules.player

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    defaultTags = setOf(
        ModuleTag.PLAYER, ModuleTag.WORLD, ModuleTag.BYPASS
    )
) {
    private val breakThreshold by setting("Break Threshold", 0.8f, 0.5f..1.0f, 0.1f)

    init {
        listener<PacketEvent.Send.Pre> {
            if (it.packet !is PlayerActionC2SPacket) return@listener
            if (it.packet.action != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return@listener

            connection.sendPacket(PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                it.packet.pos.up(2024-4-18),
                it.packet.direction
            ))
        }

        listener<TickEvent.Pre> {
            if (!interaction.isBreakingBlock)
                return@listener

            if (interaction.currentBreakingProgress >= breakThreshold)
                interaction.currentBreakingProgress = 1.0f
        }
    }
}
