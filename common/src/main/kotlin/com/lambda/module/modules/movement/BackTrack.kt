package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.ServerPacket
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.plus
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket
import net.minecraft.network.packet.s2c.play.EntityS2CPacket
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedDeque

object BackTrack : Module(
    name = "BackTrack",
    description = "Gives reach advantage by delaying your packets",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val mode by setting("Mode", Mode.FIXED)
    private val delay by setting("Delay", 500, 100..2000) { mode == Mode.FIXED }
    private val maxDelay by setting("Max Delay", 1000, 100..2000) { mode == Mode.RANGED || mode == Mode.ADAPTIVE }
    private val distance by setting("Distance", 3.0, 1.0..5.0, 0.1) { mode == Mode.RANGED || mode == Mode.ADAPTIVE }

    private val target get() = if (KillAura.isDisabled) null else KillAura.target
    private var targetPos: Vec3d? = null

    private val box = DynamicAABB()

    private const val POSITION_PACKET_SCALE = 1 / 4096.0
    private val currentTime get() = System.currentTimeMillis()
    private val packetPool = ConcurrentLinkedDeque<Pair<ServerPacket, Long>>()

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
        listener<TickEvent.Pre> {
            target?.let { target ->
                val pos = targetPos ?: target.pos
                targetPos = pos

                box.update(target.boundingBox.offset(pos - target.pos))
                poolPackets()
                return@listener
            }

            poolPackets(true)
            targetPos = null
            box.reset()
        }

        listener<RenderEvent.DynamicESP> {
            val target = target ?: return@listener

            val c1 = GuiSettings.primaryColor
            val c2 = Color.RED
            val p = target.hurtTime / 10.0
            val c = lerp(c1, c2, p)

            it.renderer.build(box, c.multAlpha(0.3), c.multAlpha(0.8))
        }

        listener<PacketEvent.Receive.Pre> { event ->
            val target = target ?: return@listener

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
                is ParticleS2CPacket, is WorldTimeUpdateS2CPacket, is WorldEventS2CPacket -> {
                    return@listener
                }
            }

            packetPool.add(packet to currentTime)
            event.cancel()
        }

        listener<ConnectionEvent.Connect> {
            packetPool.clear()
        }

        onEnable {
            poolPackets(true)
        }

        onDisable {
            poolPackets(true)
        }
    }

    private fun SafeContext.poolPackets(all: Boolean = false) {
        while (packetPool.isNotEmpty()) {
            val (packet, timing) = packetPool.poll() ?: break

            val send = all || targetPos?.let { serverPos ->
                target?.pos?.let { clientPos ->
                    mode.shouldSend(this, clientPos, serverPos, timing)
                }
            } ?: true

            if (!send) break
            connection.handlePacketSilently(packet)
        }
    }
}