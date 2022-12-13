package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.manager.managers.activity.Activity

abstract class TimedActivity(private val maxAgeMilliSec: Long) : Activity() {
    private fun checkForTimeout() {
        if (System.currentTimeMillis() > maxAgeMilliSec) {
            activityStatus = ActivityStatus.FAILURE
        }
    }
}