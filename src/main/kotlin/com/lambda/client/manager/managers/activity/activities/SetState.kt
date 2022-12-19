package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity

class SetState(private val activity: Activity, private val state: ActivityStatus) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        activity.activityStatus = state
        activityStatus = ActivityStatus.SUCCESS
    }
}