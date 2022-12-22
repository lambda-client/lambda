package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.SetState
import com.lambda.client.activity.activities.getContainerPos
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items

class BreakDownEnderChests : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getContainerPos()?.let { remotePos ->
            if (player.inventorySlots.countEmpty() == 0) {
                addSubActivities(
                    StoreItemToShulkerBox(Blocks.OBSIDIAN.item),
                    SetState(ActivityStatus.UNINITIALIZED)
                )
            } else {
                addSubActivities(
                    SwapOrMoveToItem(Blocks.ENDER_CHEST.item),
                    PlaceBlock(remotePos, Blocks.ENDER_CHEST),
                    SwapOrMoveToItem(
                        Items.DIAMOND_PICKAXE,
                        predicateItem = {
                            EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 0
                        }
                    ),
//                    SwapToBestTool(remotePos),
                    BreakBlock(
                        remotePos,
                        pickUpDrop = true,
                        mode = BreakBlock.Mode.PLAYER_CONTROLLER
                    ),
                    SetState(ActivityStatus.UNINITIALIZED)
                )
            }
        }

    }
}