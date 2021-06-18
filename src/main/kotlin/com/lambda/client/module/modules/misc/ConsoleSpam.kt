package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.client.CPacketUpdateSign
import net.minecraft.tileentity.TileEntitySign

object ConsoleSpam : Module(
    name = "ConsoleSpam",
    description = "Spams Spigot consoles by sending invalid UpdateSign packets",
    category = Category.MISC
) {
    init {
        onEnable {
            sendChatMessage("$chatName Every time you right click a sign, a warning will appear in console.")
            sendChatMessage("$chatName Use an auto clicker to automate this process.")
        }

        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketPlayerTryUseItemOnBlock) return@safeListener
            connection.sendPacket(CPacketUpdateSign(it.packet.pos, TileEntitySign().signText))
        }
    }
}