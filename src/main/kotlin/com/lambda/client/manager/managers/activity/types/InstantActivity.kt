package com.lambda.client.manager.managers.activity.types

import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

open class InstantActivity : Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (subActivities.isEmpty()) {
                activityStatus = ActivityStatus.SUCCESS
            }
        }
    }
}