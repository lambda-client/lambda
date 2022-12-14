package com.lambda.client.manager.managers.activity.types

import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

abstract class AttemptingActivity(private val attempts: Int) : Activity() {
    private var attempt = 0

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (attempt >= attempts) {
                activityStatus = ActivityStatus.FAILURE
            }
        }
    }

    fun attempt() {
        attempt++
    }
}