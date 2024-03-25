package com.lambda.core

import com.lambda.event.EventFlow.post
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener

object TimerManager {
    @JvmStatic var tickLength = 50f; private set

    init {
        unsafeListener<TickEvent.Post> {
            ClientEvent.Timer(1.0).post {
                tickLength = 50f / speed.toFloat()
            }
        }
    }
}