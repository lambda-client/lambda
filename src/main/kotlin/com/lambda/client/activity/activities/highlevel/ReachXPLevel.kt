package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.LoopingUntilActivity
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Items
import net.minecraft.util.math.BlockPos

class ReachXPLevel(
    private val desiredLevel: Int,
//    private val xpSupply: BlockPos, should be replaced with using ecosystem for stashes
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