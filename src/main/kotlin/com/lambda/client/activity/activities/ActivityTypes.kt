package com.lambda.client.activity.activities

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

interface LoopingAmountActivity {
    val loopingAmount: Int
    var loops: Int
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