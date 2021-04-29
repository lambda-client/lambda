package com.lambda.client.module.modules.chat

import com.lambda.client.command.CommandManager
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageDetection
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.text.formatValue
import com.lambda.event.listener.listener
import net.minecraft.network.play.server.SPacketChat

// TODO: When list settings are added to GUI, refactor the custom setting to be a list of usernames
// TODO: Removed feedback as it does not work on Lambda command feedback.
// Perhaps we need to restructure the message sending system, as currently the methods feel ugly.
internal object RemoteCommand : Module(
    name = "RemoteCommand",
    description = "Allow trusted players to send commands",
    category = Category.CHAT
) {
    private val allow = setting("Allow", Allow.FRIENDS)
    private val repeatAll by setting("Repeat All", false)
    private val custom by setting("Custom", "unchanged", { allow.value == Allow.CUSTOM || allow.value == Allow.FRIENDS_AND_CUSTOM })

    init {
        allow.listeners.add {
            mc.player?.let {
                if ((allow.value == Allow.CUSTOM || allow.value == Allow.FRIENDS_AND_CUSTOM) && custom == "unchanged") {
                    MessageSendHelper.sendChatMessage("$chatName Use the ${formatValue("${CommandManager.prefix}set Custom")}"
                        + " command to change the custom users list. For example, "
                        + formatValue("${CommandManager.prefix}set Custom dominika,Dewy,086"))
                }
            }
        }

        listener<PacketEvent.Receive> {
            if (it.packet !is SPacketChat) return@listener
            var message = it.packet.chatComponent.unformattedText

            if (MessageDetection.Direct.RECEIVE detectNot message) return@listener

            val username = MessageDetection.Direct.RECEIVE.playerName(message) ?: return@listener
            if (!isValidUser(username)) return@listener

            message = MessageDetection.Direct.RECEIVE.removedOrNull(message)?.toString() ?: return@listener

            MessageDetection.Command.KAMI_BLUE.removedOrNull(message)?.let { command ->
                MessageSendHelper.sendKamiCommand(command.toString())
            } ?: run {
                MessageDetection.Command.BARITONE.removedOrNull(message)?.let { command ->
                    MessageSendHelper.sendBaritoneCommand(*command.split(' ').toTypedArray())
                }
            } ?: run {
                if (repeatAll) {
                    MessageSendHelper.sendServerMessage(message)
                }
            }
        }
    }

    private fun isValidUser(username: String): Boolean {
        return when (allow.value) {
            Allow.ANYBODY -> true
            Allow.FRIENDS -> FriendManager.isFriend(username)
            Allow.CUSTOM -> isCustomUser(username)
            Allow.FRIENDS_AND_CUSTOM -> FriendManager.isFriend(username) || isCustomUser(username)
        }
    }

    private fun isCustomUser(username: String): Boolean {
        return custom.split(",").any { it.equals(username, true) }
    }

    private enum class Allow {
        ANYBODY, FRIENDS, CUSTOM, FRIENDS_AND_CUSTOM
    }
}
