package com.lambda.client.activity.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface LoopWhileActivity {
    val loopWhile: SafeClientEvent.() -> Boolean
    var currentLoops: Int

    companion object {
        fun SafeClientEvent.checkLoopingUntil(activity: Activity) {
            if (activity !is LoopWhileActivity) return

            with(activity) {
                if (!loopWhile()) return

                currentLoops++
                status = Activity.Status.UNINITIALIZED
                owner?.subActivities?.add(activity)
//                LambdaMod.LOG.info("Looping $name ($currentLoops)")
            }
        }
    }
}