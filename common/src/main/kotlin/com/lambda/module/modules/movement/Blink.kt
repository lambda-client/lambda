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
import com.lambda.util.ClientPacket
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import com.lambda.util.math.setAlpha
import com.lambda.util.math.VecUtils.minus
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
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

    private var packetPool = ConcurrentLinkedDeque<ClientPacket>()
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

            packetPool.add(event.packet)
            event.cancel()
            return@listener
        }

        listener<PacketEvent.Send.Post> { event ->
            val packet = event.packet
            if (packet !is PlayerMoveC2SPacket) return@listener

            val vec = Vec3d(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0))
            if (vec == Vec3d.ZERO) return@listener

            lastBox = player.boundingBox.offset(vec - player.pos)
        }

        listener<PacketEvent.Receive.Pre> { event ->
            if (!isActive || !shiftVelocity) return@listener

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
            }
        }

        lastVelocity?.let { velocity ->
            connection.handlePacketSilently(velocity)
            lastVelocity = null
        }
    }
}
