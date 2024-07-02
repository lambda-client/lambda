package com.lambda.module.modules.network

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.BrandCustomPayload
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket
import net.minecraft.text.ClickEvent
import java.awt.Color

object ServerSpoof : Module(
    name = "ServerSpoof",
    description = "Decide yourself if you want to accept the server resource pack.",
    defaultTags = setOf(ModuleTag.NETWORK)
) {
    private val spoofClientBrand by setting("Spoof Client Brand", true)
    private val spoofName by setting("Spoof Name", "vanilla", visibility = { spoofClientBrand })
    private val cancelResourcePack by setting("Cancel Resource Pack Loading", true)

    init {
        unsafeListener<PacketEvent.Send.Pre> {
            val packet = it.packet
            if (packet !is CustomPayloadC2SPacket) return@unsafeListener
            val payload = packet.payload
            if (payload !is BrandCustomPayload) return@unsafeListener
            if (!spoofClientBrand || payload.id != BrandCustomPayload.ID) return@unsafeListener

            payload.write(PacketByteBuf(Unpooled.buffer()).writeString(spoofName))
        }

        unsafeListener<PacketEvent.Receive.Pre> { event ->
            val packet = event.packet
            if (!cancelResourcePack) return@unsafeListener
            if (packet !is ResourcePackSendS2CPacket) return@unsafeListener

            event.cancel()

            this@ServerSpoof.info(buildText {
                literal("Canceled ${if (packet.required) "required" else "optional"} server resource pack. ")
                clickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, packet.url)) {
                    styled(color = Color.GREEN, underlined = true) {
                        literal("(Click here to download)")
                    }
                }
            })
        }
    }
}
