package com.lambda.client.command.commands

import com.google.gson.JsonParser
import com.lambda.client.command.ClientCommand
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.commons.utils.grammar
import com.lambda.client.manager.managers.UUIDManager
import com.lambda.client.util.text.MessageSendHelper

object PlaytimeCommand: ClientCommand(
    name = "playtime",
    alias = arrayOf("pt"),
    description = "Check a player's playtime on 2b2t"
) {
    private val parser = JsonParser()

    init {
        string("playerName") { playerName ->
            executeAsync("Check a player's playtime on 2b2t") {
                UUIDManager.getByName(playerName.value)?.let { profile ->
                    ConnectionUtils.requestRawJsonFrom("https://api.2b2t.vc/playtime?uuid=${profile.uuid}") {
                        MessageSendHelper.sendChatMessage("Failed querying playtime data for player: ${it.message}")
                    }?.let {
                        if (it.isEmpty()) {
                            MessageSendHelper.sendChatMessage("No data found for player: ${profile.name}")
                            return@let
                        }
                        val jsonElement = parser.parse(it)
                        val playtimeSeconds = jsonElement.asJsonObject["playtimeSeconds"].asInt
                        MessageSendHelper.sendChatMessage("${profile.name} has played for ${formatDuration(playtimeSeconds.toLong())}")
                    }
                } ?: run{
                    MessageSendHelper.sendChatMessage("Failed to find player with name ${playerName.value}")
                }
            }
        }
    }

    private fun formatDuration(durationInSeconds: Long): String {
        val secondsInMinute = 60L
        val secondsInHour = secondsInMinute * 60L
        val secondsInDay = secondsInHour * 24L
        val secondsInMonth = secondsInDay * 30L // assuming 30 days per month

        val months = durationInSeconds / secondsInMonth
        val days = (durationInSeconds % secondsInMonth) / secondsInDay
        val hours = (durationInSeconds % secondsInDay) / secondsInHour
        return buildString {
            append(if(months > 0) "${grammar(months.toInt(), "month", "months")}, " else "")
            append(if(days > 0) "${grammar(days.toInt(), "day", "days")}, " else "")
            append(grammar(hours.toInt(), "hour", "hours"))
        }
    }
}