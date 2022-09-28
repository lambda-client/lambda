package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.network.play.client.CPacketKeepAlive
import net.minecraft.network.play.server.SPacketKeepAlive

object PingSpoof : Module(
    name = "PingSpoof",
    description = "Adds delay to your packets",
    category = Category.MISC
) {
    private val delay by setting("Delay", 100, 0..2000, 25, unit = "ms")

    init {
        listener<PacketEvent.Receive> {
            if (it.packet is SPacketKeepAlive) {
                it.cancel()
                defaultScope.launch {
                    delay(delay.toLong())
                    onMainThreadSafe {
                        connection.sendPacket(CPacketKeepAlive(it.packet.id))
                    }
                }
            }
        }
    }
}
