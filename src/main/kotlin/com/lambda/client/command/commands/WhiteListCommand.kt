package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.player.ChestStealer
import com.lambda.client.util.text.MessageSendHelper

// TODO: Remove once GUI has List
object WhiteListCommand : ClientCommand(
    name = "whiteList",
    description = "Modify whiteList items for ChestStealer module"
) {
    init {
        literal("add", "+") {
            item("item") { itemArg ->
                execute("Add an item to the WhiteList") {
                    val itemName = itemArg.value.registryName!!.toString()

                    if (ChestStealer.whiteList.contains(itemName)) {
                        MessageSendHelper.sendErrorMessage("&c$itemName is already added to the WhiteList")
                    } else {
                        ChestStealer.whiteList.add(itemName)
                        MessageSendHelper.sendChatMessage("$itemName has been added to the WhiteList")
                    }
                }
            }
        }

        literal("del", "remove", "-") {
            item("item") { itemArg ->
                execute("Remove an item from the WhiteList") {
                    val itemName = itemArg.value.registryName!!.toString()

                    if (!ChestStealer.whiteList.contains(itemName)) {
                        MessageSendHelper.sendErrorMessage("&c$itemName is not in the WhiteList")
                    } else {
                        ChestStealer.whiteList.remove(itemName)
                        MessageSendHelper.sendChatMessage("$itemName has been removed from the WhiteList")
                    }
                }
            }
        }

        literal("list") {
            execute("List items in the WhiteList") {
                var list = ChestStealer.whiteList.joinToString()
                if (list.isEmpty()) list = "&cNo items!"
                MessageSendHelper.sendChatMessage("WhiteList:\n$list")
            }
        }

        literal("reset", "default") {
            execute("Reset the WhiteList to defaults") {
                ChestStealer.whiteList.resetValue()
                MessageSendHelper.sendChatMessage("WhiteList to defaults")
            }
        }

        literal("clear") {
            execute("Set the WhiteList to nothing") {
                ChestStealer.whiteList.clear()
                MessageSendHelper.sendChatMessage("WhiteList was cleared")
            }
        }
    }
}