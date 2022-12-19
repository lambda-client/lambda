package com.lambda.client.manager.managers.activity.activities

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
}

interface RenderBlockActivity {
    var renderBlockPos: BlockPos
    var color: ColorHolder
}

interface CallbackActivity {
    val owner: Activity
}

interface ThrowableActivity {
    val throwable: Throwable
}