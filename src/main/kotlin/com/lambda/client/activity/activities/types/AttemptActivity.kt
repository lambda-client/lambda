package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper

interface AttemptActivity {
    val maxAttempts: Int
    var usedAttempts: Int

    companion object {
        fun SafeClientEvent.checkAttempt(activity: Activity) {
            if (activity !is AttemptActivity) return

            with(activity) {
                if (usedAttempts >= maxAttempts) {
                    failedWith(MaxAttemptsExceededException(usedAttempts))
                } else {
                    usedAttempts++
                    initialize()
                    MessageSendHelper.sendErrorMessage("$name timed out at attempt $usedAttempts of $maxAttempts")
                }
            }
        }

        class MaxAttemptsExceededException(usedAttempts: Int) : Exception("exceeded $usedAttempts attempts")
    }
}