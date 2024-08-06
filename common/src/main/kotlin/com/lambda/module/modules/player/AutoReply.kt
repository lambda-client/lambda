package com.lambda.module.modules.player

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Formatting.string
import com.lambda.util.StringUtils.capitalize
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.util.math.Vec3d

object AutoReply : Module(
    name = "AutoReply",
    description = "Automatically Replies to messages",
    defaultTags = setOf(ModuleTag.PLAYER)
){
    private val range by setting("Reply range", 5000, 0..25000, 250, "Replies with coords if player is within range to spawn", " Blocks")
    init{
        listener<PacketEvent.Receive.Pre>{ event ->
            val packet = event.packet
            if (packet !is GameMessageS2CPacket) return@listener
            val msg = packet.content.string
            if (!msg.contains("Coords", true)) return@listener
            if (!msg.contains(" whispers: ")) return@listener
            if (!player.pos.isInRange(Vec3d.ZERO, range.toDouble())) return@listener
            val sender = msg.split(" whispers: ").first()
            connection.sendChatCommand("w $sender ${player.blockPos.string} [${world.dimensionKey.value.path.capitalize()}]")
        }
    }
}