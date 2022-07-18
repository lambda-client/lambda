package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.misc.HighwayTools
import com.lambda.client.module.modules.misc.HighwayTools.printSettings
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage

object HighwayToolsCommand : ClientCommand(
    name = "highwaytools",
    alias = arrayOf("ht", "hwt", "high"),
    description = "Customize settings of HighwayTools."
) {
    init {
        literal("distance") {
            int("distance") { distanceArg ->
                execute("Sets the target distance until the bot stops") {
                    HighwayTools.distance = distanceArg.value
                    sendChatMessage("HighwayTools will stop after (${distanceArg.value}) blocks distance. To remove the limit use distance 0")
                }
            }
        }

        literal("material", "mat") {
            block("block") { blockArg ->
                execute("Sets a block as main material") {
                    HighwayTools.material = blockArg.value
                    sendChatMessage("Set your building material to &7${blockArg.value.localizedName}&r.")
                }
            }
        }

        execute("Print settings") {
            printSettings()
        }
    }
}