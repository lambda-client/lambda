package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.LoopingAmountActivity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items

class BreakDownEnderChests(
    override val loopingAmount: Int = 0,
    override var loops: Int = 0
) : LoopingAmountActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getContainerPos()?.let { remotePos ->
            if (player.inventorySlots.countEmpty() < 2) {
                addSubActivities(
                    StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
                )
            } else {
                addSubActivities(
                    PlaceBlockSafely(remotePos, Blocks.ENDER_CHEST.defaultState),
                    SwapOrMoveToItem(
                        Items.DIAMOND_PICKAXE,
                        predicateItem = {
                            EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 0
                        }
                    ),
                    BreakBlock(
                        remotePos,
                        pickUpDrop = true,
                        minPickUpAmount = 64,
                        mode = BreakBlock.Mode.PLAYER_CONTROLLER
                    )
                )
            }
        }
    }
}