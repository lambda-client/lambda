package com.lambda.client.command.commands

import com.google.gson.JsonParser
import com.lambda.client.command.ClientCommand
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.manager.managers.UUIDManager
import com.lambda.client.util.text.MessageSendHelper
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object SeenCommand : ClientCommand(
    name = "seen",
    alias = arrayOf("lastseen"),
    description = "Check when a player was last seen"
) {

    private val parser = JsonParser()

    init {
        string("playerName") { playerName ->
            executeAsync("Check when a player was last seen") {
                UUIDManager.getByName(playerName.value)?.let outer@ { profile ->
                    ConnectionUtils.requestRawJsonFrom("https://api.2b2t.vc/seen?uuid=${profile.uuid}") {
                        MessageSendHelper.sendChatMessage("Failed querying seen data for player: ${it.message}")
                    }?.let {
                        if (it.isEmpty()) {
                            MessageSendHelper.sendChatMessage("No data found for player: ${profile.name}")
                            return@outer
                        }
                        val jsonElement = parser.parse(it)
                        val dateRaw = jsonElement.asJsonObject["time"].asString
                        val parsedDate = ZonedDateTime.parse(dateRaw).withZoneSameInstant(ZoneId.systemDefault())
                        val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.LONG)
                        MessageSendHelper.sendChatMessage("${profile.name} was last seen on ${parsedDate.format(dateFormatter)}")
                    }

                    return@executeAsync
                }

                MessageSendHelper.sendChatMessage("Failed to find player with name ${playerName.value}")
            }
        }
    }
}