package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.init.Items

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

            literal("reset", "clear") {
                execute("Resets the ignore list") {
                    BuildTools.ignoreBlocks.clear()
                    MessageSendHelper.sendChatMessage("Reset ignore list.")
                }
            }

            literal("default") {
                execute("Adds all default blocks to ignore list") {
                    BuildTools.ignoreBlocks.addAll(BuildTools.defaultIgnoreBlocks)
                    MessageSendHelper.sendChatMessage("Added all default blocks to ignore list.")
                }
            }

            execute("Lists all ignored blocks") {
                if (BuildTools.ignoreBlocks.isEmpty()) {
                    MessageSendHelper.sendChatMessage("No blocks are ignored.")
                    return@execute
                }

                MessageSendHelper.sendChatMessage("Ignored blocks: ${BuildTools.ignoreBlocks.joinToString(", ")}")
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

            literal("reset", "clear") {
                execute("Resets the eject list") {
                    BuildTools.ejectList.clear()
                    MessageSendHelper.sendChatMessage("Reset eject list.")
                }
            }

            literal("default") {
                execute("Adds all default blocks to eject list") {
                    BuildTools.ejectList.addAll(BuildTools.defaultEjectList)
                    MessageSendHelper.sendChatMessage("Added all default blocks to eject list.")
                }
            }

            execute("Lists all blocks in eject list") {
                if (BuildTools.ejectList.isEmpty()) {
                    MessageSendHelper.sendChatMessage("No blocks are in the eject list.")
                    return@execute
                }
                MessageSendHelper.sendChatMessage("Eject list: ${BuildTools.ejectList.joinToString(", ")}")
            }
        }

        literal("food") {
            item("item") { itemArg ->
                execute("Sets the food item") {
                    BuildTools.defaultFood = itemArg.value
                    MessageSendHelper.sendChatMessage("Set food item to &7${itemArg.value.registryName}&r.")
                }
            }

            literal("default") {
                execute("Sets the food item to default") {
                    BuildTools.defaultFood = Items.GOLDEN_APPLE
                    MessageSendHelper.sendChatMessage("Set food item to default.")
                }
            }

            execute("Shows the food item") {
                MessageSendHelper.sendChatMessage("Food item: &7${BuildTools.defaultFood.registryName}&r")
            }
        }

        literal("storage") {
            literal("add", "new", "+") {
                blockPos("pos1") { pos1 ->
                    blockPos("pos2") { pos2 ->
                        execute("Sets storage area") {
                            BuildTools.storagePos1.value = pos1.value
                            BuildTools.storagePos2.value = pos2.value
                            MessageSendHelper.sendChatMessage("Added storage area (${pos1.value}x${pos2.value}). Use &7${prefix}buildtools storage remove&r to remove it.")
//                            BuildTools.storageAreas.add(it.args[0].value to it.args[1].value)
//                            MessageSendHelper.sendChatMessage("Added storage area with id ${BuildTools.storageAreas.size - 1}.")
                        }
                    }
                }
            }
            literal("remove", "rem", "-") {
                int("id") {
                    execute("Not yet implemented") {
                        MessageSendHelper.sendChatMessage("Not yet implemented.")
                    }
                }
            }
        }
    }
}