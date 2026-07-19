
package com.minato.module.modules.network

import com.minato.event.events.PacketEvent
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info
import com.minato.util.text.ClickEvents
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.literal
import com.minato.util.text.styled
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.BrandCustomPayload
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket
import java.awt.Color

object ServerSpoof : Module(
    name = "ServerSpoof",
    description = "Decide yourself if you want to accept the server resource pack.",
    tag = ModuleTag.NETWORK,
) {
    private val spoofClientBrand by setting("Spoof Client Brand", true)
    private val spoofName by setting("Spoof Name", "vanilla", visibility = { spoofClientBrand })
    private val cancelResourcePack by setting("Cancel Resource Pack Loading", true)

    init {
        listenUnsafe<PacketEvent.Send.Pre> {
            val packet = it.packet
            if (packet !is CustomPayloadC2SPacket) return@listenUnsafe
            val payload = packet.payload
            if (payload !is BrandCustomPayload) return@listenUnsafe
            if (!spoofClientBrand || payload.id != BrandCustomPayload.ID) return@listenUnsafe

            payload.write(PacketByteBuf(Unpooled.buffer()).writeString(spoofName))
        }

        listenUnsafe<PacketEvent.Receive.Pre> { event ->
            val packet = event.packet
            if (!cancelResourcePack) return@listenUnsafe
            if (packet !is ResourcePackSendS2CPacket) return@listenUnsafe

            event.cancel()

            this@ServerSpoof.info(buildText {
                literal("Canceled ${if (packet.required) "required" else "optional"} server resource pack. ")
                clickEvent(ClickEvents.openUrl(packet.url)) {
                    styled(color = Color.GREEN, underlined = true) {
                        literal("(Click here to download)")
                    }
                }
            })
        }
    }
}
