package com.lambda.client.command.commands

import com.lambda.client.activity.activities.storage.types.Area
import com.lambda.client.activity.activities.storage.types.Stash
import com.lambda.client.command.ClientCommand
import com.lambda.client.command.CommandManager
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.module.modules.misc.WorldEater.info
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.item.Item

object WorldEaterCommand : ClientCommand(
    name = "worldeater",
    alias = arrayOf("we"),
    description = "World eater command"
) {
    init {
        literal("start") {
            executeSafe("Start world eater") {
                with(WorldEater) {
                    clearAllAreas()
                }
            }
        }

        literal("stop") {
            executeSafe("Stop world eater") {
                WorldEater.ownedActivity?.let {
                    with(it) {
                        cancel()
                    }
                }
            }
        }

        literal("pickup") {
            literal("add", "new", "+") {
                item("item") { itemArg ->
                    execute("Add item to pickup list") {
                        if (WorldEater.collectables.add(itemArg.value)) {
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
                        if (WorldEater.collectables.remove(itemArg.value)) {
                            MessageSendHelper.sendChatMessage("Removed &7${itemArg.value.registryName}&r from pickup list.")
                        } else {
                            MessageSendHelper.sendChatMessage("&7${itemArg.value.registryName}&r is not on the pickup list.")
                        }
                    }
                }
            }
        }

        literal("quarry") {
            literal("add", "new", "+") {
                blockPos("pos1") { pos1 ->
                    blockPos("pos2") { pos2 ->
                        execute("Adds excavating area") {
                            val area = Area(pos1.value, pos2.value)

                            WorldEater.quarries.add(area)
                            MessageSendHelper.sendChatMessage("Added excavating area $area")
                        }
                    }
                }
            }
            literal("remove", "rem", "-") {
                int("id") { id ->
                    execute("Removes excavating area") {
                        val removed = WorldEater.quarries.value.removeAt(id.value)
                        MessageSendHelper.sendChatMessage("Removed excavating area $removed")
                    }
                }
            }
            execute("Shows all quarries") {
                MessageSendHelper.sendChatMessage("Quarries:")
                WorldEater.quarries.forEachIndexed { index, volume ->
                    MessageSendHelper.sendChatMessage("  &7$index&r: $volume")
                }
            }
        }

        literal("stash") {
            literal("add", "new", "+") {
                blockPos("pos1") { pos1 ->
                    blockPos("pos2") { pos2 ->
                        greedy("items") {
                            execute("Adds stash area") {
                                val safeArgs = CommandManager.tryParseArgument(args.joinToString(" ")) ?: return@execute

                                val items = safeArgs.mapNotNull { Item.getByNameOrId(it) }
                                val stash = Stash(Area(pos1.value, pos2.value), items)

                                WorldEater.stashes.value.add(stash)
                                MessageSendHelper.sendChatMessage("Added stash $stash")
                            }
                        }
                    }
                }
            }
            literal("remove", "rem", "-") {
                int("id") { id ->
                    execute("Removes stash area") {
                        val removed = WorldEater.stashes.value.removeAt(id.value)
                        MessageSendHelper.sendChatMessage("Removed stash area $removed")
                    }
                }
            }
            execute("Shows all stashes") {
                MessageSendHelper.sendChatMessage("Stashes:")
                WorldEater.stashes.forEachIndexed { index, stash ->
                    MessageSendHelper.sendChatMessage("  &7$index&r: $stash")
                }
            }
        }

        literal("dropOff") {
            literal("add", "new", "+") {
                blockPos("pos1") { pos1 ->
                    blockPos("pos2") { pos2 ->
                        greedy("items") {
                            execute("Adds drop off area") {
                                val safeArgs = CommandManager.tryParseArgument(args.joinToString(" ")) ?: return@execute

                                val items = safeArgs.mapNotNull { Item.getByNameOrId(it) }
                                val dropOff = Stash(Area(pos1.value, pos2.value), items)

                                WorldEater.dropOff.value.add(dropOff)
                                MessageSendHelper.sendChatMessage("Added drop-off area $dropOff")
                            }
                        }
                    }
                }
            }
            literal("remove", "rem", "-") {
                int("id") { id ->
                    execute("Removes excavating area") {
                        val removed = WorldEater.dropOff.value.removeAt(id.value)
                        MessageSendHelper.sendChatMessage("Removed drop-off area $removed")
                    }
                }
            }
            execute("Shows all drop-off areas") {
                MessageSendHelper.sendChatMessage("Stashes:")
                WorldEater.dropOff.forEachIndexed { index, dropOff ->
                    MessageSendHelper.sendChatMessage("  &7$index&r: $dropOff")
                }
            }
        }

        executeSafe("General setting info") {
            MessageSendHelper.sendChatMessage(info())
        }
    }
}