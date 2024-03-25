package com.lambda.module.modules.client

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.PlayerPacketManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.warn
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.Vec3d
import java.awt.Color

// ToDo: Should also include last packet info as HUD element and connection state. We may find a better name.
object Rubberband : Module(
    name = "Rubberband",
    description = "Info about rubberbands",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val showLastPacketInfo by setting("Show Last Packet", true)
    private val showConnectionState by setting("Show Connection State", true)
    private val showRubberbandInfo by setting("Show Rubberband Info", true)

    init {
        listener<PacketEvent.Receive.Pre> { event ->
            if (!showRubberbandInfo) return@listener
            if (event.packet !is PlayerPositionLookS2CPacket) return@listener

            if (PlayerPacketManager.configurations.isEmpty()) {
                this@Rubberband.warn("Position was reverted")
                return@listener
            }

            val newPos = Vec3d(event.packet.x, event.packet.y, event.packet.z)
            val last = PlayerPacketManager.configurations.minBy {
                it.position distSq newPos
            }

            this@Rubberband.warn(buildText {
                literal("Reverted position by ")
                color(Color.YELLOW) {
                    literal("${PlayerPacketManager.configurations.reversed().indexOf(last) + 1}")
                }
                literal(" ticks (derivation: ")
                color(Color.YELLOW) {
                    literal("%.3f".format(last.position dist newPos))
                }
                literal(")")
            })
        }
    }
}