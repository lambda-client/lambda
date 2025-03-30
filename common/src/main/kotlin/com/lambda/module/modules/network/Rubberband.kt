/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.network

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.PlayerPacketManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.warn
import com.lambda.util.math.dist
import com.lambda.util.math.distSq
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.Vec3d
import java.awt.Color

// ToDo: Should also include last packet info as HUD element and connection state.
//  We should find a better name.
//  Also should pause baritone on lag.
object Rubberband : Module(
    name = "Rubberband",
    description = "Info about rubberbands",
    defaultTags = setOf(ModuleTag.NETWORK, ModuleTag.DEBUG)
) {
    private val showLastPacketInfo by setting("Show Last Packet", true)
    private val showConnectionState by setting("Show Connection State", true)
    private val showRubberbandInfo by setting("Show Rubberband Info", true)

    init {
        listen<PacketEvent.Receive.Pre> { event ->
            if (!showRubberbandInfo) return@listen
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen

            if (PlayerPacketManager.configurations.isEmpty()) {
                this@Rubberband.warn("Position was reverted")
                return@listen
            }

            val newPos = event.packet.change.position
            val last = PlayerPacketManager.configurations.minBy {
                it.position distSq newPos
            }

            this@Rubberband.warn(buildText {
                literal("Reverted position by ")
                color(Color.YELLOW) {
                    literal("${PlayerPacketManager.configurations.toList().asReversed().indexOf(last) + 1}")
                }
                literal(" ticks (deviation: ")
                color(Color.YELLOW) {
                    literal("%.3f".format(last.position dist newPos))
                }
                literal(")")
            })
        }
    }
}
