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

package com.lambda.core

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.util.collections.LimitedOrderedSet
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket
import net.minecraft.util.Util

@Suppress("unused")
object PingHandler : Loadable {
    private val pings: LimitedOrderedSet<Long> = LimitedOrderedSet(100)

    override fun load(): String {
        listen<TickEvent.Pre> {
            connection.sendPacket(QueryPingC2SPacket(Util.getMeasuringTimeMs()))
        }

        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PingResultS2CPacket) return@listen
            pings.add(Util.getMeasuringTimeMs() - event.packet.startTime)
        }

        return "Loaded Ping Manager"
    }

    val lastPing: Long
        get() = pings.lastOrNull() ?: 0
}
