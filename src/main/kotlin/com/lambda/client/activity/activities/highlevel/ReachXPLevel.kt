package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.LoopingUntilActivity
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Items

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