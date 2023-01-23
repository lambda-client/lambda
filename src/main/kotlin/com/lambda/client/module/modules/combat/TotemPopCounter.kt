package com.lambda.client.module.modules.combat

import com.lambda.client.LambdaMod
import com.lambda.client.commons.extension.synchronized
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.EnumTextColor
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.text.format
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.server.SPacketEntityStatus
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object TotemPopCounter : Module(
    name = "TotemPopCounter",
    description = "Displays the number of times a player has popped",
    category = Category.COMBAT
) {
    private val countFriends by setting("Count Friends", true)
    private val countSelf by setting("Count Self", true)
    private val announceSetting by setting("Announce", Announce.CLIENT)
    private val thanksTo by setting("Thanks To", false)
    private val colorName by setting("Color Name", EnumTextColor.DARK_PURPLE)
    private val colorNumber by setting("Color Number", EnumTextColor.LIGHT_PURPLE)

    private enum class Announce {
        CLIENT, EVERYONE
    }

    val popCountMap = WeakHashMap<EntityPlayer, Int>().synchronized()
    private var wasSent = false

    init {
        onDisable {
            popCountMap.clear()
            wasSent = true
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet !is SPacketEntityStatus || it.packet.opCode.toInt() != 35 || !player.isEntityAlive) return@safeListener
            val player = (it.packet.getEntity(world) as? EntityPlayer) ?: return@safeListener

            if (friendCheck(player) && selfCheck(player)) {
                val count = popCountMap.getOrDefault(player, 0) + 1
                popCountMap[player] = count

                val isSelf = player == this.player
                val message = "${formatName(player)} popped ${formatNumber(count)} ${plural(count)}${ending(isSelf)}"
                sendMessage(message, !isSelf && isPublic)
            }
        }

        listener<ConnectionEvent.Disconnect> {
            popCountMap.clear()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            if (!player.isEntityAlive) {
                if (!wasSent) {
                    popCountMap.clear()
                    wasSent = true
                }
                return@safeListener
            }

            wasSent = false

            popCountMap.entries.removeIf { (player, count) ->
                if (player == this.player || player.isEntityAlive) {
                    false
                } else {
                    val message = "${formatName(player)} died after popping ${formatNumber(count)} ${plural(count)}${ending(false)}"
                    sendMessage(message, isPublic)
                    true
                }
            }
        }
    }

    private fun friendCheck(player: EntityPlayer) = countFriends || !FriendManager.isFriend(player.name)

    private fun selfCheck(player: EntityPlayer) = countSelf || player != mc.player

    private fun formatName(player: EntityPlayer) =
        colorName.textFormatting format when {
            player == mc.player -> {
                "I"
            }
            FriendManager.isFriend(player.name) -> {
                if (isPublic) "My friend ${player.name}, " else "Your friend ${player.name}, "
            }
            else -> {
                player.name
            }
        }

    private val isPublic: Boolean
        get() = announceSetting == Announce.EVERYONE

    private fun formatNumber(message: Int) = colorNumber.textFormatting format message

    private fun plural(count: Int) = if (count == 1) "totem" else "totems"

    private fun ending(self: Boolean): String = if (!self && thanksTo) " thanks to ${LambdaMod.NAME} !" else "!"

    private fun sendMessage(message: String, public: Boolean) {
        if (public) {
            sendServerMessage(TextFormatting.getTextWithoutFormattingCodes(message))
        } else {
            MessageSendHelper.sendChatMessage("$chatName $message")
        }
    }
}
