package com.lambda.client.command.commands

import com.google.gson.JsonParser
import com.lambda.client.command.ClientCommand
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.commons.utils.grammar
import com.lambda.client.manager.managers.UUIDManager
import com.lambda.client.util.text.MessageSendHelper
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object PlaytimeCommand: ClientCommand(
    name = "playtime",
    alias = arrayOf("pt"),
    description = "Check a player's playtime on 2b2t"
) {
    private val parser = JsonParser()

    init {
        string("playerName") { playerName ->
            executeAsync("Check a player's playtime on 2b2t") {
                UUIDManager.getByName(playerName.value)?.let outer@ { profile ->
                    ConnectionUtils.requestRawJsonFrom("https://api.2b2t.vc/playtime?uuid=${profile.uuid}") {
                        MessageSendHelper.sendChatMessage("Failed querying playtime data for player: ${it.message}")
                    }?.let {
                        if (it.isEmpty()) {
                            MessageSendHelper.sendChatMessage("No data found for player: ${profile.name}")
                            return@outer
                        }
                        val jsonElement = parser.parse(it)
                        val playtimeSeconds = jsonElement.asJsonObject["playtimeSeconds"].asDouble
                        MessageSendHelper.sendChatMessage("${profile.name} has played for ${
                            playtimeSeconds.toDuration(DurationUnit.SECONDS)
                        }")
                    }

                    return@executeAsync
                }

                MessageSendHelper.sendChatMessage("Failed to find player with name ${playerName.value}")
            }
        }
    }
}