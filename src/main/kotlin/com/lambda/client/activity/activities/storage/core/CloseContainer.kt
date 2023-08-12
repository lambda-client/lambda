package com.lambda.client.activity.activities.storage.core

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class CloseContainer : Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.closeScreen()
        success()
    }
}