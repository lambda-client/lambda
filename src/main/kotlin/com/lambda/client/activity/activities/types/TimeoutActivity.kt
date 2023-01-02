package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper

interface TimeoutActivity {
    val timeout: Long

    companion object {
        fun SafeClientEvent.checkTimeout(activity: Activity) {
            if (activity !is TimeoutActivity) return

            with(activity) {
                if (age <= timeout) return

                if (activity is AttemptActivity) {
                    with(activity) {
                        usedAttempts++
                        initialize()
                        MessageSendHelper.sendErrorMessage("$name timed out at attempt $usedAttempts of $maxAttempts")
                    }
                } else {
                    failedWith(TimeoutException(age, timeout))
                }
            }
        }

        class TimeoutException(age: Long, timeout: Long): Exception("Exceeded maximum age ($age) of $timeout ms")
    }
}