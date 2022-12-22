package com.lambda.client.activity.activities

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class Wait(
    override val delay: Long,
    override var creationTime: Long = 0L
) : DelayedActivity, Activity() {
    override fun SafeClientEvent.onDelayedActivity() {
        activityStatus = ActivityStatus.SUCCESS
    }
}