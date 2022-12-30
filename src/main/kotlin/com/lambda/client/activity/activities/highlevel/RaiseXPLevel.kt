package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Items
import net.minecraft.util.math.BlockPos

class RaiseXPLevel(
    private val desiredLevel: Int,
    private val xpSupply: BlockPos
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            TakeOffArmor(),
            SwapOrMoveToItem(
                Items.EXPERIENCE_BOTTLE,
                useShulkerBoxes = false
            ),
            UseThrowableOnEntity(player)
        )
    }
}