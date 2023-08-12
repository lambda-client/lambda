package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BreakBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.core.PlaceContainer
import com.lambda.client.activity.activities.storage.types.*
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
                    ShulkerTransaction(ContainerAction.PUSH, StackSelection().apply {
                        selection = isBlock(Blocks.OBSIDIAN)
                    })
                )
                return
            }

            failedWith(NoSpaceLeftInInventoryException())
            return
        }

        addSubActivities(
            PlaceContainer(StackSelection().apply {
                selection = isBlock(Blocks.ENDER_CHEST)
            })
        )
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is PlaceContainer) return

        addSubActivities(
            AcquireItemInActiveHand(StackSelection().apply {
                selection = isItem(Items.DIAMOND_PICKAXE) and hasEnchantment(Enchantments.SILK_TOUCH).not()
            }),
            BreakBlock(
                childActivity.containerPos,
                collectDrops = true,
                minCollectAmount = 64
            )
        )
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
}