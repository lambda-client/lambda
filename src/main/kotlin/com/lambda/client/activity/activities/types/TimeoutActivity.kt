package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface TimeoutActivity {
    val timeout: Long

    companion object {
        fun SafeClientEvent.checkTimeout(activity: Activity) {
            if (activity !is TimeoutActivity) return

            with(activity) {
                if (age <= timeout) return

                failedWith(TimeoutException(age, timeout))
            }
        }

        class TimeoutException(age: Long, timeout: Long) : Exception("Exceeded maximum age ${age}ms / ${timeout}ms")
    }
}