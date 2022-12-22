package com.lambda.client.activity.activities

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class SetState(private val state: ActivityStatus) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        owner.activityStatus = state
        activityStatus = ActivityStatus.SUCCESS
    }
}