package com.lambda.module.modules.player

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Formatting.string
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket

object AutoReply : Module(
    name = "AutoReply",
    description = "Automatically Replies to messages",
    defaultTags = setOf(ModuleTag.PLAYER)
){
    init{
        listener<PacketEvent.Receive.Post>{ event ->
              val packet = event.packet;
            if (packet !is GameMessageS2CPacket) return@listener
            if (!packet.content.string.contains("Coords", true)) return@listener
            if (!packet.content.string.contains(" whispers: ", true)) return@listener
            var msg = packet.content.string
            val sender = msg.split(" whispers: ").first()
            connection.sendChatCommand("w $sender ${player.pos.string} [${world.dimension.toString()}]")



        }
    }

}