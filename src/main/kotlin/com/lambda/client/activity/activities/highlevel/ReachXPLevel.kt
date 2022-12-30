package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.LoopingUntilActivity
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.DumpInventory
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager.onSuccess
import net.minecraft.init.Items
import net.minecraft.util.math.BlockPos

class ReachXPLevel(
    private val desiredLevel: Int,
    override val loopUntil: SafeClientEvent.() -> Boolean = {
        player.experienceLevel >= desiredLevel
    },
    override var currentLoops: Int = 0
) : LoopingUntilActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {


        addSubActivities(
            TakeOffArmor(),
            SwapOrMoveToItem(Items.EXPERIENCE_BOTTLE),
            UseThrowableOnEntity(player)
        )
    }
}