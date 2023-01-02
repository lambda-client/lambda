package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.LoopingAmountActivity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.BreakBlockRaw
import com.lambda.client.activity.activities.interaction.PlaceBlock
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
    override val maxLoops: Int = 0,
    override var currentLoops: Int = 0
) : LoopingAmountActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (player.inventorySlots.countEmpty() < 2) {
            addSubActivities(
                StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
            )
            return
        }

        getContainerPos()?.let { remotePos ->
            addSubActivities(
                PlaceBlock(remotePos, Blocks.ENDER_CHEST.defaultState),
                SwapOrMoveToItem(
                    Items.DIAMOND_PICKAXE,
                    predicateItem = {
                        EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 0
                    }
                ),
                BreakBlockRaw(
                    remotePos,
                    collectDrops = true,
                    minCollectAmount = 64
                )
            )
        } ?: run {
            failedWith(NoEnderChestPosFoundException())
        }
    }

    class NoEnderChestPosFoundException : Exception("No free ender chest position was found")
}