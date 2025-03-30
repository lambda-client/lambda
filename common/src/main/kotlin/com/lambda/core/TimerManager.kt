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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import net.minecraft.client.render.RenderTickCounter
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.milliseconds

object TimerManager : Loadable {
    var lastTickLength = 50.0

    override fun load() = "Loaded Timer Manager"

    private const val TICK_DELAY = 50L
    private var start = 0L

    val length: Double
        get() {
            var length = 50.0

            ClientEvent.TimerUpdate(1.0).post {
                length /= speed
            }

            lastTickLength = length
            return length
        }

    init {
        listen<TickEvent.Pre> {
            (mc.renderTickCounter as RenderTickCounter.Dynamic)
                .beginRenderTick(length.milliseconds.inWholeNanoseconds, false)
        }

        // ToDo: Use minecraft fixed tick counter
        fixedRateTimer(
            daemon = true,
            name = "Scheduler-Lambda-Tick",
            initialDelay = 0,
            period = TICK_DELAY
        ) {
            if (start == 0L) start = System.currentTimeMillis()
            ClientEvent.FixedTick(this).post()
        }
    }
}
