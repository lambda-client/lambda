package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface ConcurrentActivity {
    val maxInstancesPerTick: Int
    var currentInstances: Int
    val root: Activity

    companion object {
        fun SafeClientEvent.doConcurrent(activity: Activity): Boolean {
            if (activity !is ConcurrentActivity) return false

            with(activity) {
                if (currentInstances >= maxInstancesPerTick) return false
                currentInstances++
//                root.refresh(activity.owner.owner)
                root.addSubActivities(activity)
            }
            return true
        }

        fun SafeClientEvent.checkConcurrent(activity: Activity): Boolean {
            if (activity !is ConcurrentActivity) return false

            activity.currentInstances = 0
            return true
        }
    }
}