package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.client.CPacketCustomPayload

object BeaconSelector : Module(
    name = "BeaconSelector",
    description = "Choose any of the 5 beacon effects regardless of beacon base height",
    category = Category.MISC
) {
    private var doCancelPacket = true
    var effect = -1

    init {
        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketCustomPayload || !doCancelPacket || it.packet.channelName != "MC|Beacon") return@safeListener
            doCancelPacket = false
            it.packet.bufferData.readInt() // primary
            val secondary = it.packet.bufferData.readInt() // secondary
            it.cancel()
            PacketBuffer(Unpooled.buffer()).apply {
                writeInt(effect)
                writeInt(secondary)
            }.also { buffer ->
                connection.sendPacket(CPacketCustomPayload("MC|Beacon", buffer))
            }
            doCancelPacket = true
        }
    }
}