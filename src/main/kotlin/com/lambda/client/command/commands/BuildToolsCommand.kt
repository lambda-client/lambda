package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.text.MessageSendHelper

object BuildToolsCommand : ClientCommand(
    name = "buildtools",
    alias = arrayOf("bt", "build"),
    description = "Customize settings of BuildTools"
) {
    init {
        literal("ignore") {
            literal("add", "new", "+") {
                block("block") { blockArg ->
                    execute("Adds a block to ignore list") {
                        val added = BuildTools.ignoreBlocks.add(blockArg.value.registryName.toString())
                        if (added) {
                            MessageSendHelper.sendChatMessage("Added &7${blockArg.value.localizedName}&r to ignore list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is already ignored.")
                        }
                    }
                }
            }

            literal("remove", "rem", "-", "del") {
                block("block") { blockArg ->
                    execute("Removes a block from ignore list") {
                        val removed = BuildTools.ignoreBlocks.remove(blockArg.value.registryName.toString())
                        if (removed) {
                            MessageSendHelper.sendChatMessage("Removed &7${blockArg.value.localizedName}&r from ignore list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is not yet ignored.")
                        }
                    }
                }
            }
        }

        literal("eject") {
            literal("add", "new", "+") {
                block("block") { blockArg ->
                    execute("Adds a block to eject list") {
                        val added = BuildTools.ejectList.add(blockArg.value.registryName.toString())
                        if (added) {
                            MessageSendHelper.sendChatMessage("Added &7${blockArg.value.localizedName}&r to eject list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is already in eject list.")
                        }
                    }
                }
            }

            literal("remove", "rem", "-", "del") {
                block("block") { blockArg ->
                    execute("Removes a block from eject list") {
                        val removed = BuildTools.ejectList.remove(blockArg.value.registryName.toString())
                        if (removed) {
                            MessageSendHelper.sendChatMessage("Removed &7${blockArg.value.localizedName}&r from eject list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${blockArg.value.localizedName}&r is not yet in the eject list.")
                        }
                    }
                }
            }
        }

        literal("food") {
            item("item") { itemArg ->
                execute("Sets the food item") {
                    BuildTools.defaultFood = itemArg.value
                    MessageSendHelper.sendChatMessage("Set food item to &7${itemArg.value.registryName}&r.")
                }
            }
        }
    }
}