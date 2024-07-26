package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.util.PacketUtils.sendPacketSilently
import com.lambda.util.math.ColorUtils.setAlpha
import net.minecraft.network.ClientConnection
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.util.concurrent.ConcurrentLinkedDeque

object Blink : Module(
    name = "Blink",
    description = "Holds packets",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private var delay by setting("Delay", 500, 50..10000, 10)
    private val shiftVelocity by setting("Shift velocity", true)
    private val requiresAura by setting("Requires Aura", false)

    private val isActive get() = (KillAura.isEnabled && KillAura.target != null) || !requiresAura

    private var packetPool = ConcurrentLinkedDeque<Packet<*>>()
    private var lastVelocity: EntityVelocityUpdateS2CPacket? = null
    private var lastUpdate = 0L

    private var box = DynamicAABB()
    private var lastBox = Box(BlockPos.ORIGIN)

    init {
        listener<RenderEvent.World> {
            val time = System.currentTimeMillis()

            if (isActive && time - lastUpdate < delay) return@listener
            lastUpdate = time

            poolPackets()
        }

        listener<RenderEvent.DynamicESP> { event ->
            val color = GuiSettings.primaryColor
            event.renderer.build(box.update(lastBox), color.setAlpha(0.3), color)
        }

        listener<PacketEvent.Send.Pre> { event ->
            if (!isActive) return@listener
            if (!connection.connection.isOpen) return@listener

            packetPool.add(event.packet)
            event.cancel()
            return@listener
        }

        listener<PacketEvent.Receive.Pre> { event ->
            if (!isActive || !shiftVelocity) return@listener
            if (!connection.connection.isOpen) return@listener
            if (connection.connection.packetListener?.accepts(event.packet) == false) return@listener

            if (event.packet !is EntityVelocityUpdateS2CPacket) return@listener
            if (event.packet.id != player.id) return@listener

            lastVelocity = event.packet
            event.cancel()
            return@listener
        }

        onDisable {
            poolPackets()
        }
    }

    private fun SafeContext.poolPackets() {
        while (packetPool.isNotEmpty()) {
            packetPool.poll().let { packet ->
                connection.sendPacketSilently(packet)
                connection.connection.packetsSentCounter++

                if (packet is PlayerMoveC2SPacket && packet.changesPosition()) {
                    lastBox = player.boundingBox
                        .offset(player.pos.negate())
                        .offset(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0))
                }
            }
        }

        lastVelocity?.let { velocity ->
            ClientConnection.handlePacket(velocity, connection.connection.packetListener)
            connection.connection.packetsReceivedCounter++
            lastVelocity = null
        }
    }
}
