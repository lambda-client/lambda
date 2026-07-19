
package com.minato.module.modules.network

import com.minato.event.events.PacketEvent
import com.minato.event.events.PlayerPacketEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.warn
import com.minato.util.collections.LimitedOrderedSet
import com.minato.util.math.dist
import com.minato.util.math.distSq
import com.minato.util.text.buildText
import com.minato.util.text.color
import com.minato.util.text.literal
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.Vec3d
import java.awt.Color

// ToDo: Should also include last packet info as HUD element and connection state.
//  We should find a better name.
object Rubberband : Module(
    name = "Rubberband",
    description = "Info about rubberbands",
    tag = ModuleTag.NETWORK,
) {
    private val showLastPacketInfo by setting("Show Last Packet", true)
    private val showConnectionState by setting("Show Connection State", true)
    private val showRubberbandInfo by setting("Show Rubberband Info", true)

    val configurations = LimitedOrderedSet<Vec3d>(100)

    init {
        listen<PacketEvent.Receive.Pre> { event ->
            if (!showRubberbandInfo) return@listen
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen

            if (configurations.isEmpty()) {
                this@Rubberband.warn("Position was reverted")
                return@listen
            }

            val newPos = event.packet.change.position
            val last = configurations.minBy {
                it distSq newPos
            }

            this@Rubberband.warn(buildText {
                literal("Reverted position by ")
                color(Color.YELLOW) {
                    literal("${configurations.toList().asReversed().indexOf(last) + 1}")
                }
                literal(" ticks (deviation: ")
                color(Color.YELLOW) {
                    literal("%.3f".format(last dist newPos))
                }
                literal(")")
            })
        }

        listen<PlayerPacketEvent.Send> { configurations.add(with(it.packet) { Vec3d(x, y, z) }) }
    }
}
