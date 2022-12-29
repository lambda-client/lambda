package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.interaction.UseThrowableItem
import com.lambda.client.activity.activities.inventory.InventoryTransaction
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.util.math.BlockPos

class RaiseXPLevel(
    private val desiredLevel: Int,
    private val xpSupply: BlockPos
) : InstantActivity, Activity() {

    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            TakeOffArmor(),
            SwapOrMoveToItem(Items.EXPERIENCE_BOTTLE, useShulkerBoxes = false),
            UseThrowableItem()
        )
    }

}