package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.misc.HighwayTools
import com.lambda.client.util.text.MessageSendHelper

object HighwayToolsCommand : ClientCommand(
    name = "highwaytools",
    alias = arrayOf("ht", "hwt", "high"),
    description = "Customize settings of HighwayTools."
) {
    init {
        literal("material", "mat") {
            block("block") { blockArg ->
                execute("Sets a block as main material") {
                    HighwayTools.material = blockArg.value
                    MessageSendHelper.sendChatMessage("Set your building material to &7${blockArg.value.localizedName}&r.")
                }
            }
        }

        literal("distance") {
            int("distance") { distanceArg ->
                execute("Sets the distance between the player and the highway") {
                    HighwayTools.distance = distanceArg.value
                    MessageSendHelper.sendChatMessage("Set your distance to &7${distanceArg.value}&r.")
                }
            }
        }
        
        literal("start") {
            executeSafe("Starts the highway") {
                with(HighwayTools) {
                    start()
                }
            }
        }
    }
}