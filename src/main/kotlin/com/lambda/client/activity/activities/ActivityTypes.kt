package com.lambda.client.activity.activities

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.Vec2f
import net.minecraft.util.math.BlockPos

interface TimeoutActivity {
    val timeout: Long
}

interface CompoundActivity

interface ConcurrentActivity {
    val max: Int
}

interface LoopingActivity {
    val loopingAmount: Int
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