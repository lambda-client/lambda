package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.player.ChestStealer
import com.lambda.client.util.text.MessageSendHelper

// TODO: Remove once GUI has List
object BlackListCommand : ClientCommand(
    name = "blackList",
    description = "Modify blackList items for ChestStealer module"
) {
    init {
        literal("add", "+") {
            item("item") { itemArg ->
                execute("Add an item to the BlackList") {
                    val itemName = itemArg.value.registryName!!.toString()

                    if (ChestStealer.blackList.contains(itemName)) {
                        MessageSendHelper.sendErrorMessage("&c$itemName is already added to the BlackList")
                    } else {
                        ChestStealer.blackList.add(itemName)
                        MessageSendHelper.sendChatMessage("$itemName has been added to the BlackList")
                    }
                }
            }
        }

        literal("del", "remove", "-") {
            item("item") { itemArg ->
                execute("Remove an item from the BlackList") {
                    val itemName = itemArg.value.registryName!!.toString()

                    if (!ChestStealer.blackList.contains(itemName)) {
                        MessageSendHelper.sendErrorMessage("&c$itemName is not in the BlackList")
                    } else {
                        ChestStealer.blackList.remove(itemName)
                        MessageSendHelper.sendChatMessage("$itemName has been removed from the BlackList")
                    }
                }
            }
        }

        literal("list") {
            execute("List items in the BlackList") {
                var list = ChestStealer.blackList.joinToString()
                if (list.isEmpty()) list = "&cNo items!"
                MessageSendHelper.sendChatMessage("BlackList:\n$list")
            }
        }

        literal("reset", "default") {
            execute("Reset the BlackList to defaults") {
                ChestStealer.blackList.resetValue()
                MessageSendHelper.sendChatMessage("BlackList to defaults")
            }
        }

        literal("clear") {
            execute("Set the BlackList to nothing") {
                ChestStealer.blackList.clear()
                MessageSendHelper.sendChatMessage("BlackList was cleared")
            }
        }
    }
}