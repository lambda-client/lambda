package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity

class Wait(
    override val delay: Long,
    override var creationTime: Long = 0L
) : DelayedActivity, Activity() {
    override fun SafeClientEvent.onDelayedActivity() {
        activityStatus = ActivityStatus.SUCCESS
    }
}