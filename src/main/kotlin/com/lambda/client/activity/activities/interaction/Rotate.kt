package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class Rotate(
    override var rotation: Vec2f
) : RotatingActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            success()
        }
    }
}