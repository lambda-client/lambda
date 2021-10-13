package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.item.ItemWrittenBook
import net.minecraft.network.play.client.CPacketClickWindow

/**
 * @author IronException
 * Used with permission from ForgeHax
 * https://github.com/fr1kin/ForgeHax/blob/bb522f8/src/main/java/com/matt/forgehax/mods/AntiBookKick.java
 * Permission (and ForgeHax is MIT licensed):
 * https://discordapp.com/channels/573954110454366214/634010802403409931/693919755647844352
 */
object AntiBookKick : Module(
    name = "AntiBookKick",
    category = Category.MISC,
    description = "Prevents being kicked by clicking on books",
    showOnArray = false
) {
    init {
        safeListener<PacketEvent.PostSend> {
            if (it.packet !is CPacketClickWindow) return@safeListener
            if (it.packet.clickedItem.item !is ItemWrittenBook) return@safeListener

            it.cancel()
            MessageSendHelper.sendWarningMessage(chatName
                + " Don't click the book \""
                + it.packet.clickedItem.displayName
                + "\", shift click it instead!")
            player.openContainer.slotClick(it.packet.slotId, it.packet.usedButton, it.packet.clickType, player)
        }
    }
}