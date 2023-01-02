package com.lambda.client.activity.activities.utils

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.DelayedActivity
import com.lambda.client.event.SafeClientEvent

class Wait(
    override val delay: Long
) : DelayedActivity, Activity() {
    override fun SafeClientEvent.onDelayedActivity() {
        success()
    }
}