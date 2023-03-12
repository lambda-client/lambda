package com.lambda.client.activity.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface DelayedActivity {
    val delay: Long

    fun SafeClientEvent.onDelayedActivity()

    companion object {
        fun SafeClientEvent.checkDelayed(activity: Activity) {
            if (activity !is DelayedActivity) return

            with(activity) {
                if (age > delay) onDelayedActivity()
            }
        }
    }
}