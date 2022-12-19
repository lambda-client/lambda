package com.lambda.client.manager.managers.activity.activities.interaction

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity

class CloseContainer : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.closeScreen()
        activityStatus = ActivityStatus.SUCCESS
    }
}