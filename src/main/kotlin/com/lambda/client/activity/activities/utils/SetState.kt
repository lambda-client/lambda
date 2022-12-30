package com.lambda.client.activity.activities.utils

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class SetState(private val state: ActivityStatus) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        with(owner) {
            when (state) {
                ActivityStatus.UNINITIALIZED -> activityStatus = state
                ActivityStatus.SUCCESS -> onSuccess()
                ActivityStatus.RUNNING -> activityStatus = state
                ActivityStatus.PENDING -> activityStatus = state
                ActivityStatus.FAILURE -> onFailure()
            }
        }
    }
}