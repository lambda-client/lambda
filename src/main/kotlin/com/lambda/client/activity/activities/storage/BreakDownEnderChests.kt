package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.activities.storage.types.ContainerAction
import com.lambda.client.activity.activities.storage.types.ItemInfo
import com.lambda.client.activity.activities.storage.types.ItemOrder
import com.lambda.client.activity.activities.storage.types.ShulkerOrder
import com.lambda.client.activity.types.RepeatingActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.items.countItem
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.item.ItemStack

class BreakDownEnderChests(
    override val maximumRepeats: Int = 0,
    override var repeated: Int = 0
) : RepeatingActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val freeSlots = player.inventorySlots.filter { slot ->
            BuildTools.ejectList.contains(slot.stack.item.registryName.toString())
                || slot.stack.isEmpty
        }

        if (freeSlots.isEmpty()) {
            if (player.inventorySlots.countItem(Blocks.OBSIDIAN.item) > 0) {
                addSubActivities(
                    ShulkerTransaction(ShulkerOrder(ContainerAction.PUSH, Blocks.OBSIDIAN.item, 0))
                )
                return
            }

            failedWith(NoSpaceLeftInInventoryException())
            return
        }

        // ToDo: Better way to find ender chest
        addSubActivities(
            PlaceContainer(ItemStack(Blocks.ENDER_CHEST, 1, 0), onlyItem = true)
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            AcquireItemInActiveHand(ItemInfo(
                Items.DIAMOND_PICKAXE,
                predicate = {
                    EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 0
                }
            )),
            BreakBlock(
                childActivity.containerPos,
                collectDrops = true,
                minCollectAmount = 64
            )
        )
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
}