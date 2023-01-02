package com.lambda.client.activity.activities.types

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface LoopingUntilActivity {
    val loopWhile: SafeClientEvent.() -> Boolean
    var currentLoops: Int

    companion object {
        fun SafeClientEvent.checkLoopingUntil(activity: Activity) {
            if (activity !is LoopingUntilActivity) return

            with(activity) {
                if (!loopWhile()) return

                currentLoops++
                activityStatus = Activity.ActivityStatus.UNINITIALIZED
                owner.subActivities.add(activity)
                LambdaMod.LOG.info("Looping $name ($currentLoops)")
            }
        }
    }
}