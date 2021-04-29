package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.chat.ChatTimestamp
import com.lambda.client.util.text.MessageSendHelper

object FakeMessageCommand : ClientCommand(
    name = "fakemsg",
    alias = arrayOf("fm", "fakemsg"),
    description = "Send a client side fake message, use & with formatting codes."
) {
    init {
        greedy("message") { messageArg ->
            execute("Use & for color formatting") {
                MessageSendHelper.sendRawChatMessage(getTime() + messageArg.value.replace('&', '§'))
            }
        }
    }

    private fun getTime() = if (ChatTimestamp.isEnabled) ChatTimestamp.formattedTime else ""
}