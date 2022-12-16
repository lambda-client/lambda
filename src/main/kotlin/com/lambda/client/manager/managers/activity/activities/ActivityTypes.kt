package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.color.ColorHolder
import net.minecraft.util.math.BlockPos

interface TimeoutActivity {
    val timeout: Long
    var creationTime: Long
}

interface InstantActivity

interface DelayedActivity {
    val delay: Long
    var creationTime: Long

    fun SafeClientEvent.onDelayedActivity()
}

interface AttemptActivity {
    val maxAttempts: Int
    var usedAttempts: Int

    companion object {
        fun <T : AttemptActivity> T.doCheck() {
            if (usedAttempts >= maxAttempts) {
                (this as? Activity)?.activityStatus = Activity.ActivityStatus.FAILURE
                LambdaMod.LOG.error("AttemptActivity failed after $maxAttempts attempts!")
            }
        }
    }
}

interface RenderBlockActivity {
    var renderBlockPos: BlockPos
    var color: ColorHolder
}