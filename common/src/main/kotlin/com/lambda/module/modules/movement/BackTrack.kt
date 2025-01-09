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

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.ofBox
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.util.ClientPacket
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import com.lambda.util.ServerPacket
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import net.minecraft.entity.LivingEntity
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedDeque

object BackTrack : Module(
    name = "BackTrack",
    description = "Gives reach advantage by delaying your packets",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val outbound by setting("Outbound", true)
    private val mode by setting("Mode", Mode.FIXED)
    private val delay by setting("Delay", 500, 100..2000) { mode == Mode.FIXED }
    private val maxDelay by setting("Max Delay", 1000, 100..2000) { mode == Mode.RANGED || mode == Mode.ADAPTIVE }
    private val distance by setting("Distance", 3.0, 1.0..5.0, 0.1) { mode == Mode.RANGED || mode == Mode.ADAPTIVE }

    private var target: LivingEntity? = null
    private var targetPos: Vec3d? = null

    private val box = DynamicAABB()

    private const val POSITION_PACKET_SCALE = 1 / 4096.0
    private val currentTime get() = System.currentTimeMillis()

    private val sendPool = ConcurrentLinkedDeque<Pair<ClientPacket, Long>>()
    private val receivePool = ConcurrentLinkedDeque<Pair<ServerPacket, Long>>()

    enum class Mode(val shouldSend: SafeContext.(Vec3d, Vec3d, Long) -> Boolean) {
        FIXED({ _, _, timing ->
            currentTime > timing + delay
        }),
        RANGED({ _, serverPos, timing ->
            val serverDist = player.pos dist serverPos
            currentTime > timing + maxDelay * serverDist.coerceIn(0.0, distance) / distance
        }),
        ADAPTIVE({ clientPos, serverPos, timing ->
            val clientDist = player.pos dist clientPos
            val serverDist = player.pos dist serverPos
            val advantage = serverDist - clientDist
            currentTime > timing + maxDelay * advantage.coerceIn(0.0, distance) / distance
        })
    }

    init {
        listen<TickEvent.Pre> {
            val prevTarget = target
            target = if (KillAura.isDisabled) null else KillAura.target
            val currentTarget = target

            if (prevTarget != currentTarget || currentTarget == null) {
                poolPackets(true)
                targetPos = null
                box.reset()
                return@listen
            }

            val pos = targetPos ?: currentTarget.pos
            targetPos = pos

            box.update(currentTarget.boundingBox.offset(pos - currentTarget.pos))
            poolPackets()
        }

        listen<RenderEvent.DynamicESP> {
            val target = target ?: return@listen

            val c1 = GuiSettings.primaryColor
            val c2 = Color.RED
            val p = target.hurtTime / 10.0
            val c = lerp(p, c1, c2)

            it.renderer.ofBox(box, c.multAlpha(0.3), c.multAlpha(0.8))
        }

        listen<PacketEvent.Send.Pre> { event ->
            if (!outbound || target == null) return@listen
            sendPool.add(event.packet to currentTime)
            event.cancel()
        }

        listen<PacketEvent.Receive.Pre> { event ->
            val target = target ?: return@listen

            val packet = event.packet

            when (packet) {
                is EntityS2CPacket -> {
                    if (target.id == packet.id) {
                        targetPos = targetPos?.plus(
                            Vec3d(
                                packet.deltaX * POSITION_PACKET_SCALE,
                                packet.deltaY * POSITION_PACKET_SCALE,
                                packet.deltaZ * POSITION_PACKET_SCALE
                            )
                        )
                    }
                }

                is EntityPositionS2CPacket -> {
                    if (target.id == packet.id) {
                        targetPos = Vec3d(packet.x, packet.y, packet.z)
                    }
                }

                is PlaySoundS2CPacket, is PlaySoundFromEntityS2CPacket, is StopSoundS2CPacket,
                    /*is EntityStatusS2CPacket,*/ is EntityStatusEffectS2CPacket, is EntityAnimationS2CPacket,
                is ParticleS2CPacket, is WorldTimeUpdateS2CPacket, is WorldEventS2CPacket,
                    -> {
                    return@listen
                }
            }

            receivePool.add(packet to currentTime)
            event.cancel()
        }

        listen<ConnectionEvent.Connect.Pre> {
            receivePool.clear()
            sendPool.clear()
        }

        onEnable {
            receivePool.clear()
            sendPool.clear()
        }

        onDisable {
            poolPackets(true)
        }
    }

    private fun SafeContext.poolPackets(all: Boolean = false) {
        while (receivePool.isNotEmpty()) {
            val (packet, timing) = receivePool.poll() ?: break

            val receive = all || targetPos?.let { serverPos ->
                target?.pos?.let { clientPos ->
                    mode.shouldSend(this, clientPos, serverPos, timing)
                }
            } ?: true

            if (!receive) break
            connection.handlePacketSilently(packet)
        }

        while (sendPool.isNotEmpty()) {
            val (packet, timing) = sendPool.poll() ?: break

            val send = all || targetPos?.let { serverPos ->
                target?.pos?.let { clientPos ->
                    mode.shouldSend(this, clientPos, serverPos, timing)
                }
            } ?: true

            if (!send) break
            connection.sendPacketSilently(packet)
        }
    }
}
