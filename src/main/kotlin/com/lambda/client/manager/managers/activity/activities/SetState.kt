package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity

class SetState(private val state: ActivityStatus) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        owner.activityStatus = state
        activityStatus = ActivityStatus.SUCCESS
    }
}