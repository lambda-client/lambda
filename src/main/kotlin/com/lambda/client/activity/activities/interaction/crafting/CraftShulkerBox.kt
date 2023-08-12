package com.lambda.client.activity.activities.interaction.crafting

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.types.StackSelection
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Blocks
import net.minecraft.init.Items

class CraftShulkerBox : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            AcquireItemInActiveHand(StackSelection().apply { selection = isItem(Items.SHULKER_SHELL) }),
            AcquireItemInActiveHand(StackSelection().apply { selection = isBlock(Blocks.CHEST) }),
//            InventoryTransaction(0, )
        )
    }
}