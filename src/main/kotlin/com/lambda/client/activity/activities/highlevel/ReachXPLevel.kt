package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.TakeOffArmor
import com.lambda.client.activity.activities.types.LoopWhileActivity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.init.Items

class ReachXPLevel(
    private val desiredLevel: Int,
    override val loopWhile: SafeClientEvent.() -> Boolean = {
        player.experienceLevel < desiredLevel
    },
    override var currentLoops: Int = 0
) : LoopWhileActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        addSubActivities(
            TakeOffArmor(),
            AcquireItemInActiveHand(Items.EXPERIENCE_BOTTLE),
            UseThrowableOnEntity(player)
        )
    }
}