package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.command.commands.BuildToolsCommand.blockPos
import com.lambda.client.command.commands.BuildToolsCommand.execute
import com.lambda.client.command.commands.BuildToolsCommand.int
import com.lambda.client.command.commands.BuildToolsCommand.literal
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.text.MessageSendHelper

object WorldEaterCommand : ClientCommand(
    name = "worldeater",
    alias = arrayOf("we"),
    description = "World eater command"
) {
    init {
        literal("start") {
            execute("Start world eater") {
                WorldEater.startClearingArea()
            }
        }

        literal("pickup") {
            literal("add", "new", "+") {
                item("item") { itemArg ->
                    execute("Add item to pickup list") {
                        val added = WorldEater.collectables.add(itemArg.value.registryName.toString())
                        if (added) {
                            MessageSendHelper.sendChatMessage("Added &7${itemArg.value.registryName}&r to pickup list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${itemArg.value.registryName}&r is already on the pickup list.")
                        }
                    }
                }
            }

            literal("remove") {
                item("item") { itemArg ->
                    execute("Remove item from pickup list") {
                        val removed = WorldEater.collectables.remove(itemArg.value.registryName.toString())
                        if (removed) {
                            MessageSendHelper.sendChatMessage("Removed &7${itemArg.value.registryName}&r from pickup list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${itemArg.value.registryName}&r is not on the pickup list.")
                        }
                    }
                }
            }
        }

        literal("area") {
            literal("add", "new", "+") {
                blockPos("pos1") { pos1 ->
                    blockPos("pos2") { pos2 ->
                        execute("Sets excavating area") {
                            WorldEater.pos1.value = pos1.value
                            WorldEater.pos2.value = pos2.value
                            MessageSendHelper.sendChatMessage("Added excavating area (${pos1.value}x${pos2.value}).")
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

        execute("General setting info") {
            MessageSendHelper.sendChatMessage("World eater settings:")
            MessageSendHelper.sendChatMessage("  &7${WorldEater.collectables.size}&r items on pickup list.")
            MessageSendHelper.sendChatMessage("  &7${WorldEater.pos1.value.asString()}&r is position 1.")
            MessageSendHelper.sendChatMessage("  &7${WorldEater.pos2.value.asString()}&r is position 2.")
        }
    }
}