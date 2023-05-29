package com.lambda.client.activity.activities.interaction.crafting

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.core.InventoryTransaction
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.item
import net.minecraft.init.Blocks
import net.minecraft.init.Items

class CraftShulkerBox : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            AcquireItemInActiveHand(Items.SHULKER_SHELL),
            AcquireItemInActiveHand(Blocks.CHEST.item),
//            InventoryTransaction(0, )
        )
    }
}