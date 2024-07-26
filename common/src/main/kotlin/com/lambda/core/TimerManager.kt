package com.lambda.core

import com.lambda.event.EventFlow.post
import com.lambda.event.events.ClientEvent

object TimerManager : Loadable {
    var lastTickLength: Float = 50f

    fun getLength(): Float {
        var length = 50f

        ClientEvent.Timer(1.0).post {
            length /= speed.toFloat()
        }

        lastTickLength = length
        return length
    }
}