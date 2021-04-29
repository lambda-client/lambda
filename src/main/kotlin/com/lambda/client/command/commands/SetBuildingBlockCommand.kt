package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.module.modules.player.InventoryManager
import com.lambda.client.util.items.block
import com.lambda.client.util.items.id
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.block.BlockAir

// TODO: Remove once GUI has Block settings
object SetBuildingBlockCommand : ClientCommand(
    name = "setbuildingblock",
    description = "Set the default building block"
) {
    init {
        executeSafe {
            val heldItem = player.inventory.getCurrentItem()
            when {
                heldItem.isEmpty -> {
                    InventoryManager.buildingBlockID = 0
                    MessageSendHelper.sendChatMessage("Building block has been reset")
                }
                heldItem.item.block !is BlockAir -> {
                    InventoryManager.buildingBlockID = heldItem.item.id
                    MessageSendHelper.sendChatMessage("Building block has been set to ${heldItem.displayName}")
                }
                else -> {
                    MessageSendHelper.sendChatMessage("You are not holding a valid block")
                }
            }
        }
    }
}