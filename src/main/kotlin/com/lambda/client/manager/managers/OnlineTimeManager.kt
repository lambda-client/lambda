package com.lambda.client.manager.managers

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import kotlin.time.Duration
import kotlin.time.TimeSource

object OnlineTimeManager: Manager {

    private var connectTime = TimeSource.Monotonic.markNow()

    init {
        listener<ConnectionEvent.Connect> {
            connectTime = TimeSource.Monotonic.markNow()
        }
    }

    fun getOnlineTime(): Duration {
        return connectTime.elapsedNow()
    }
}