package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper

interface AttemptActivity {
    val maxAttempts: Int
    var usedAttempts: Int

    companion object {
        fun SafeClientEvent.checkAttempt(activity: Activity, causeException: Exception): Boolean {
            if (activity !is AttemptActivity) return false
            if (causeException is MaxAttemptsExceededException
                || causeException is TimeoutActivity.Companion.TimeoutException) return false

            with(activity) {
                if (usedAttempts >= maxAttempts) {
                    failedWith(MaxAttemptsExceededException(usedAttempts, causeException))
                } else {
                    usedAttempts++
                    MessageSendHelper.sendErrorMessage("$name caused ${causeException::class.simpleName}: ${causeException.message}. Attempt $usedAttempts of $maxAttempts restarting...")
                    subActivities.clear()
                    initialize()
                }
            }
            return true
        }

        class MaxAttemptsExceededException(usedAttempts: Int, causeException: Exception) : Exception("Exceeded $usedAttempts attempts caused by ${causeException::class.simpleName}: ${causeException.message}")
    }
}