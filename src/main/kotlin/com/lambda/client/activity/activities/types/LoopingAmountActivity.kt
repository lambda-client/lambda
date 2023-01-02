package com.lambda.client.activity.activities.types

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager.activityStatus
import com.lambda.client.manager.managers.ActivityManager.name
import com.lambda.client.manager.managers.ActivityManager.owner

interface LoopingAmountActivity {
    val maxLoops: Int
    var currentLoops: Int

    companion object {
        fun checkLoopingAmount(activity: Activity) {
            if (activity !is LoopingAmountActivity) return

            with(activity) {
                if (currentLoops++ >= maxLoops || maxLoops != 0) return

                activityStatus = Activity.ActivityStatus.UNINITIALIZED
                owner.subActivities.add(activity)
                LambdaMod.LOG.info("Looping $name [$currentLoops/${if (maxLoops == 0) "∞" else maxLoops}] ")
            }
        }
    }
}