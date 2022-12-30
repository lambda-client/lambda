package com.lambda.client.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.Vec2f
import net.minecraft.util.math.BlockPos

interface TimeoutActivity {
    val timeout: Long
}

interface ConcurrentActivity {
    val maxActivities: Int
}

interface LoopingAmountActivity {
    val maxLoops: Int
    var currentLoops: Int
}

interface LoopingUntilActivity {
    val loopUntil: SafeClientEvent.() -> Boolean
    var currentLoops: Int
}

interface LoopingTimeActivity {
    val loopUntilTimestamp: Long
}

interface RotatingActivity {
    var rotation: Vec2f
}

interface DelayedActivity {
    val delay: Long

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