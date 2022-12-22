package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.event.SafeClientEvent

class CloseContainer : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.closeScreen()
        activityStatus = ActivityStatus.SUCCESS
    }
}