package com.lambda.core

import com.lambda.core.lifecycle.Loadable
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.util.collections.LimitedOrderedSet
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket
import net.minecraft.util.Util

object PingManager : Loadable {
    private val pings: LimitedOrderedSet<Long> = LimitedOrderedSet(100)
    private const val INTERVAL = 1

    val lastPing: Long
        get() = pings.lastOrNull() ?: 0

    init {
        listener<TickEvent.Pre> {
            connection.sendPacket(QueryPingC2SPacket(Util.getMeasuringTimeMs()))
        }

        listener<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PingResultS2CPacket) return@listener

            pings.add(Util.getMeasuringTimeMs() - event.packet.startTime)
        }
    }
}
