
package com.minato.interaction.handlers

import com.minato.core.Loadable
import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.util.collections.LimitedOrderedSet
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
