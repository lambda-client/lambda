/*
 * Copyright 2025 Lambda
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

package com.lambda.util

import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket

object ServerTPS {
    // Server sends exactly one world time update every 20 server ticks (one per second).
    private val updateHistory = LimitedDecayQueue<Long>(61, 60000)
    private var lastUpdate = 0L

    init {
        listen<PacketEvent.Receive.Pre>({ 10000 }) {
            if (it.packet !is WorldTimeUpdateS2CPacket) return@listen
            val currentTime = System.currentTimeMillis()

            if (lastUpdate != 0L) {
                updateHistory.add(currentTime - lastUpdate)
            }

            lastUpdate = currentTime
        }

        listen<ConnectionEvent.Disconnect> {
            updateHistory.clear()
            lastUpdate = 0
        }
    }

    fun recentData(tickFormat: TickFormat = TickFormat.Mspt) =
        updateHistory.map { tickFormat.value(it).toFloat() }.toFloatArray()

    @Suppress("unused")
    enum class TickFormat(
        val value: (Long) -> Double,
        override val displayName: String,
        override val description: String,
        val unit: String = ""
    ) : NamedEnum, Describable {
        Tps({ it / 50.0 }, "TPS", "Ticks Per Second", " t/s"),
        Mspt({ it / 20.0 }, "MSPT", "Milliseconds Per Tick", " ms/t"),
        Normalized({ it / 1000.0 }, "nTPS", "Normalized Ticks Per Second"),
        Percentage({ it / 10.0 }, "TPS%", "Deviation from 20 TPS","%")
    }
}
