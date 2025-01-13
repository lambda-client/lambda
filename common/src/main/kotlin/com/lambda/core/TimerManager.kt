/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.core

import com.lambda.event.EventFlow.post
import com.lambda.event.events.ClientEvent
import kotlin.concurrent.fixedRateTimer

object TimerManager : Loadable {
    var lastTickLength: Float = 50f

    override fun load() = "Loaded Timer Manager"

    private const val TICK_DELAY = 50L
    private const val TICK_DELAY_NANOS = TICK_DELAY * 1_000_000L
    private var start = 0L
    val fixedTickDelta get() = (System.nanoTime() - start).mod(TICK_DELAY_NANOS).toDouble() / TICK_DELAY_NANOS

    init {
        fixedRateTimer(
            daemon = true,
            name = "Scheduler-Lambda-Tick",
            initialDelay = 0,
            period = TICK_DELAY
        ) {
            if (start == 0L) start = System.nanoTime()
            ClientEvent.FixedTick(this).post()
        }
    }

    fun getLength(): Float {
        var length = 50f

        ClientEvent.TimerUpdate(1.0).post {
            length /= speed.toFloat()
        }

        lastTickLength = length
        return length
    }
}
