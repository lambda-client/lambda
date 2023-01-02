package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.AttemptActivity.Companion.checkAttempt
import com.lambda.client.event.SafeClientEvent

interface TimeoutActivity {
    val timeout: Long

    companion object {
        fun SafeClientEvent.checkTimeout(activity: Activity) {
            if (activity !is TimeoutActivity) return

            with(activity) {
                if (age <= timeout) return

                if (activity is AttemptActivity) {
                    checkAttempt(activity)
                } else {
                    failedWith(TimeoutException(age, timeout))
                }
            }
        }

        class TimeoutException(age: Long, timeout: Long): Exception("exceeded maximum age ($age) of $timeout ms")
    }
}