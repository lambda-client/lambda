package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.render.Search
import com.lambda.client.util.items.shulkerList
import com.lambda.client.util.items.signsList
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.formatValue
import net.minecraft.init.Blocks

// TODO: Remove once GUI has List
object SearchCommand : ClientCommand(
    name = "search",
    description = "Manage search blocks"
) {
    private val warningBlocks = hashSetOf(Blocks.GRASS, Blocks.END_STONE, Blocks.LAVA, Blocks.FLOWING_LAVA,
        Blocks.BEDROCK, Blocks.NETHERRACK, Blocks.DIRT, Blocks.WATER, Blocks.FLOWING_WATER, Blocks.STONE)

    init {
        literal("add", "+") {
            literal("shulker_box") {
                execute("Add all shulker box types to search") {
                    Search.blockSearchList.editValue { searchList -> shulkerList.map { it.registryName.toString() }.forEach { searchList.add(it) } }
                    MessageSendHelper.sendChatMessage("All shulker boxes have been added to block search")
                }
            }
            literal("sign") {
                execute("Add all signs to search") {
                    Search.blockSearchList.editValue { searchList -> signsList.map { it.registryName.toString() }.forEach { searchList.add(it) } }
                    MessageSendHelper.sendChatMessage("All signs have been added to block search")
                }
            }
            block("block") { blockArg ->
                literal("force") {
                    execute("Force add a block to search list") {
                        val blockName = blockArg.value.registryName.toString()
                        addBlock(blockName)
                    }
                }
                int("dimension") { dimArg ->
                    execute("Add a block to dimension filter") {
                        val blockName = blockArg.value.registryName.toString()
                        val dim = dimArg.value
                        val dims = Search.blockSearchDimensionFilter.value.find { dimFilter -> dimFilter.searchKey == blockName }?.dim
                        if (dims != null && !dims.contains(dim)) {
                            dims.add(dim)
                        } else {
                            Search.blockSearchDimensionFilter.value.add(Search.DimensionFilter(blockName, linkedSetOf(dim)))
                        }
                        MessageSendHelper.sendChatMessage("Block search filter added for $blockName in dimension ${dimArg.value}")
                    }
                }
                execute("Add a block to search list") {
                    val block = blockArg.value
                    val blockName = blockArg.value.registryName.toString()

                    if (warningBlocks.contains(block)) {
                        MessageSendHelper.sendWarningMessage("Your world contains lots of ${formatValue(blockName)}, " +
                            "it might cause extreme lag to add it. " +
                            "If you are sure you want to add it run ${formatValue("$prefixName add $blockName force")}"
                        )
                    } else {
                        addBlock(blockName)
                    }
                }
            }
            entity("entity") { entityArg ->
                int("dimension") {dimArg ->
                    execute("Add an entity to dimension filter") {
                        val entityName = entityArg.value
                        val dim = dimArg.value
                        val dims = Search.entitySearchDimensionFilter.value.find { dimFilter -> dimFilter.searchKey == entityName }?.dim
                        if (dims != null && !dims.contains(dim)) {
                            dims.add(dim)
                        } else {
                            Search.entitySearchDimensionFilter.value.add(Search.DimensionFilter(entityName, linkedSetOf(dim)))
                        }
                        MessageSendHelper.sendChatMessage("Entity search filter added for $entityName in dimension ${dimArg.value}")
                    }
                }

                execute("Add an entity to search list") {
                    val entityName = entityArg.value
                    if (Search.entitySearchList.contains(entityName)) {
                        MessageSendHelper.sendChatMessage("$entityName is already added to search list")
                        return@execute
                    }
                    Search.entitySearchList.editValue { it.add(entityName) }
                    MessageSendHelper.sendChatMessage("$entityName has been added to search list")
                }
            }
        }

        literal("remove", "-") {
            literal("shulker_box") {
                execute("Remove all shulker boxes from search") {
                    Search.blockSearchList.editValue { searchList -> shulkerList.map { it.registryName.toString() }.forEach { searchList.remove(it) } }
                    MessageSendHelper.sendChatMessage("Removed all shulker boxes from block search")
                }
            }
            literal("sign") {
                execute("Remove all signs from search") {
                    Search.blockSearchList.editValue { searchList -> signsList.map { it.registryName.toString() }.forEach { searchList.remove(it) } }
                    MessageSendHelper.sendChatMessage("Removed all signs from block search")
                }
            }
            block("block") { blockArg ->
                int("dimension") {dimArg ->
                    execute("Remove a block from dimension filter") {
                        val blockName = blockArg.value.registryName.toString()
                        val dim = dimArg.value
                        val dims = Search.blockSearchDimensionFilter.value.find { dimFilter -> dimFilter.searchKey == blockName }?.dim
                        if (dims != null) {
                            dims.remove(dim)
                            if (dims.isEmpty()) {
                                Search.blockSearchDimensionFilter.value.removeIf { it.searchKey == blockName }
                            }
                        }
                        MessageSendHelper.sendChatMessage("Block search filter removed for $blockName in dimension ${dimArg.value}")
                    }
                }
                execute("Remove a block from search list") {
                    val blockName = blockArg.value.registryName.toString()

                    if (!Search.blockSearchList.contains(blockName)) {
                        MessageSendHelper.sendErrorMessage("You do not have ${formatValue(blockName)} added to search block list")
                    } else {
                        Search.blockSearchList.editValue { it.remove(blockName) }
                        MessageSendHelper.sendChatMessage("Removed ${formatValue(blockName)} from search block list")
                    }
                }
            }
            entity("entity") {entityArg ->
                int("dimension") {dimArg ->
                    execute("Remove an entity from dimension filter") {
                        val entityName = entityArg.value
                        val dim = dimArg.value
                        val dims = Search.entitySearchDimensionFilter.value.find { dimFilter -> dimFilter.searchKey == entityName }?.dim
                        if (dims != null) {
                            dims.remove(dim)
                            if (dims.isEmpty()) {
                                Search.entitySearchDimensionFilter.value.removeIf { it.searchKey == entityName }
                            }
                        }
                        MessageSendHelper.sendChatMessage("Entity search filter removed for $entityName in dimension ${dimArg.value}")
                    }
                }
                execute("Remove an entity from search list") {
                    val entityName = entityArg.value
                    Search.entitySearchList.editValue { it.remove(entityName) }
                    MessageSendHelper.sendChatMessage("Removed $entityName from search list")
                }
            }
        }

        literal("set", "=") {
            block("block") { blockArg ->
                execute("Set the search list to one block") {
                    val blockName = blockArg.value.registryName.toString()

                    Search.blockSearchList.editValue {
                        it.clear()
                        it.add(blockName)
                    }
                    MessageSendHelper.sendChatMessage("Set the search block list to ${formatValue(blockName)}")
                }
            }
            entity("entity") { entityArg ->
                execute("Sets the search list to one entity") {
                    val entityName = entityArg.value
                    Search.entitySearchList.editValue {
                        it.clear()
                        it.add(entityName)
                    }
                    MessageSendHelper.sendChatMessage("Set the entity search list to $entityName")
                }
            }
        }

        literal("reset", "default") {
            execute("Reset the search list to defaults") {
                Search.blockSearchList.resetValue()
                Search.entitySearchList.resetValue()
                Search.blockSearchDimensionFilter.resetValue()
                Search.entitySearchDimensionFilter.resetValue()
                MessageSendHelper.sendChatMessage("Reset the search list to defaults")
            }
        }

        literal("list") {
            execute("Print search list") {
                MessageSendHelper.sendChatMessage("Blocks: ${Search.blockSearchList.joinToString()}")
                if (Search.blockSearchDimensionFilter.value.isNotEmpty()) {
                    MessageSendHelper.sendChatMessage("Block dimension filter: ${Search.blockSearchDimensionFilter.value}")
                }
                MessageSendHelper.sendChatMessage("Entities ${Search.entitySearchList.joinToString()}")
                if (Search.entitySearchDimensionFilter.value.isNotEmpty()) {
                    MessageSendHelper.sendChatMessage("Entity dimension filter: ${Search.entitySearchDimensionFilter.value}")
                }
            }
        }

        literal("clear") {
            execute("Set the search list to nothing") {
                Search.blockSearchList.editValue { it.clear() }
                Search.entitySearchList.editValue { it.clear() }
                Search.blockSearchDimensionFilter.editValue { it.clear() }
                Search.entitySearchDimensionFilter.editValue { it.clear() }
                MessageSendHelper.sendChatMessage("Cleared the search block list")
            }
        }

        literal("override") {
            execute("Override the Intel Integrated GPU check") {
                Search.overrideWarning = true
                MessageSendHelper.sendWarningMessage("Override for Intel Integrated GPUs enabled!")
            }
        }
    }

    private fun addBlock(blockName: String) {
        if (blockName == "minecraft:air") {
            MessageSendHelper.sendChatMessage("You can't add ${formatValue(blockName)} to the search block list")
            return
        }

        if (Search.blockSearchList.contains(blockName)) {
            MessageSendHelper.sendErrorMessage("${formatValue(blockName)} is already added to the search block list")
        } else {
            Search.blockSearchList.editValue { it.add(blockName) }
            MessageSendHelper.sendChatMessage("${formatValue(blockName)} has been added to the search block list")
        }
    }
}