package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.player.Scaffold
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.formatValue
import net.minecraft.init.Blocks

object ScaffoldCommand : ClientCommand(
    name = "scaffold",
    description = "Manage scaffold whitelist/blacklist"
) {
    private val allShulkerBoxes = listOf(Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.SILVER_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX,
        Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX)
        .map { it.registryName.toString() }
        .toList()

    init {
        literal("whitelist", "wl") {
            literal("add", "+") {
                literal("shulker_box") {
                    execute("Add all shulker box types to whitelist") {
                        Scaffold.blockSelectionWhitelist.editValue { whitelist -> allShulkerBoxes.forEach { whitelist.add(it) } }
                        MessageSendHelper.sendChatMessage("All shulker boxes have been added to whitelist")
                    }
                }
                block("block") { blockArg ->
                    execute("Add a block to Scaffold whitelist") {
                        val blockName = blockArg.value.registryName.toString()
                        if (Scaffold.blockSelectionWhitelist.contains(blockName)) {
                            MessageSendHelper.sendErrorMessage("${formatValue(blockName)} is already added to scaffold whitelist")
                        } else {
                            Scaffold.blockSelectionWhitelist.editValue { it.add(blockName) }
                            MessageSendHelper.sendChatMessage("${formatValue(blockName)} has been added to scaffold whitelist")
                        }
                    }
                }
            }
            literal("del", "-") {
                literal("shulker_box") {
                    execute("Remove all shulker box types from whitelist") {
                        Scaffold.blockSelectionWhitelist.editValue { whitelist -> allShulkerBoxes.forEach { whitelist.remove(it) } }
                        MessageSendHelper.sendChatMessage("All shulker boxes have been removed from whitelist")
                    }
                }
                block("block") { blockArg ->
                    execute("Removes a block from the Scaffold whitelist") {
                        val blockName = blockArg.value.registryName.toString()
                        Scaffold.blockSelectionWhitelist.editValue { it.remove(blockName) }
                        MessageSendHelper.sendChatMessage("${formatValue(blockName)} has been removed from scaffold whitelist")
                    }
                }
            }
            literal("clear", "c") {
                execute {
                    Scaffold.blockSelectionWhitelist.editValue { it.clear() }
                    MessageSendHelper.sendChatMessage("Whitelist has been cleared")
                }
            }
            literal("list") {
                execute {
                    MessageSendHelper.sendChatMessage("Blocks: ${Scaffold.blockSelectionWhitelist.joinToString()}")
                }
            }
        }
        literal("blacklist", "bl") {
            literal("add", "+") {
                literal("shulker_box") {
                    execute("Add all shulker box types to blacklist") {
                        Scaffold.blockSelectionBlacklist.editValue { blacklist -> allShulkerBoxes.forEach { blacklist.add(it) } }
                        MessageSendHelper.sendChatMessage("All shulker boxes have been added to blacklist")
                    }
                }
                block("block") { blockArg ->
                    execute("Add a block to Scaffold blacklist") {
                        val blockName = blockArg.value.registryName.toString()
                        if (Scaffold.blockSelectionBlacklist.contains(blockName)) {
                            MessageSendHelper.sendErrorMessage("${formatValue(blockName)} is already added to scaffold blacklist")
                        } else {
                            Scaffold.blockSelectionBlacklist.editValue { it.add(blockName) }
                            MessageSendHelper.sendChatMessage("${formatValue(blockName)} has been added to scaffold blacklist")
                        }
                    }
                }
            }
            literal("del", "-") {
                literal("shulker_box") {
                    execute("Remove all shulker box types from blacklist") {
                        Scaffold.blockSelectionBlacklist.editValue { blacklist -> allShulkerBoxes.forEach { blacklist.remove(it) } }
                        MessageSendHelper.sendChatMessage("All shulker boxes have been removed from blacklist")
                    }
                }
                block("block") { blockArg ->
                    execute("Removes a block from the Scaffold blacklist") {
                        val blockName = blockArg.value.registryName.toString()
                        Scaffold.blockSelectionBlacklist.editValue { it.remove(blockName) }
                        MessageSendHelper.sendChatMessage("${formatValue(blockName)} has been removed from scaffold blacklist")
                    }
                }
            }
            literal("clear", "c") {
                execute {
                    Scaffold.blockSelectionBlacklist.editValue { it.clear() }
                    MessageSendHelper.sendChatMessage("Blacklist has been cleared")
                }
            }
            literal("list") {
                execute {
                    MessageSendHelper.sendChatMessage("Blocks: ${Scaffold.blockSelectionBlacklist.joinToString()}")
                }
            }
        }
    }
}