package com.lambda.client.manager.managers

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import java.time.Duration
import java.time.Instant

object OnlineTimeManager: Manager {

    private var connectTime: Instant = Instant.EPOCH

    init {
        listener<ConnectionEvent.Connect> {
            connectTime = Instant.now()
        }
    }

    fun getOnlineTime(): Duration {
        return Duration.between(connectTime, Instant.now())
    }
}