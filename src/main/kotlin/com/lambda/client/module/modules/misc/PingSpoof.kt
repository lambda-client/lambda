package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.event.listener.listener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.network.play.client.CPacketKeepAlive
import net.minecraft.network.play.server.SPacketKeepAlive

internal object PingSpoof : Module(
    name = "PingSpoof",
    category = Category.MISC,
    description = "Cancels or adds delay to your ping packets"
) {
    private val delay = setting("Delay", 100, 0..2000, 25)

    init {
        listener<PacketEvent.Receive> {
            if (it.packet is SPacketKeepAlive) {
                it.cancel()
                defaultScope.launch {
                    delay(delay.value.toLong())
                    onMainThreadSafe {
                        connection.sendPacket(CPacketKeepAlive(it.packet.id))
                    }
                }
            }
        }
    }
}
