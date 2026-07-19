
package com.minato.interaction.handlers

import com.minato.core.Loadable
import com.minato.event.EventFlow.post
import com.minato.event.events.ClientEvent
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.milliseconds

object TimerHandler : Loadable {
    const val DEFAULT_LENGTH = 50.0
    var lastTickLength = 50.0

    override fun load() = "Loaded Timer Manager"

    val length: Double
        get() {
            var length = DEFAULT_LENGTH

            ClientEvent.TimerUpdate(1.0).post {
                length /= speed
            }

            lastTickLength = length
            return length
        }

    init {
        fixedRateTimer(
            daemon = true,
            name = "Scheduler-Minato-Tick",
            period = 50.milliseconds.inWholeMilliseconds,
        ) {
            ClientEvent.FixedTick(this).post()
        }
    }
}
