/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.handlers

import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ClientEvent
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
            name = "Scheduler-Lambda-Tick",
            period = 50.milliseconds.inWholeMilliseconds,
        ) {
            ClientEvent.FixedTick(this).post()
        }
    }
}
