package com.lambda.client.activity.types

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

interface RepeatingActivity {
    val maximumRepeats: Int
    var repeated: Int

    companion object {
        fun checkRepeat(activity: Activity) {
            if (activity !is RepeatingActivity) return

            with(activity) {
                if (repeated++ >= maximumRepeats && maximumRepeats != 0) return

                status = Activity.Status.UNINITIALIZED
                owner?.subActivities?.add(activity)
//                LambdaMod.LOG.info("Looping $name [$currentLoops/${if (maxLoops == 0) "∞" else maxLoops}] ")
            }
        }
    }
}