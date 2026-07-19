
package com.minato.module.modules.movement

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.event.events.PacketEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.minato.graphics.util.DynamicAABB
import com.minato.gui.components.ClickGuiLayout
import com.minato.module.Module
import com.minato.module.modules.combat.KillAura
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.PacketUtils.handlePacketSilently
import com.minato.util.PacketUtils.sendPacketSilently
import com.minato.util.ServerPacket
import com.minato.util.extension.tickDelta
import com.minato.util.math.minus
import com.minato.util.math.setAlpha
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentLinkedDeque

object Blink : Module(
    name = "Blink",
    description = "Holds packets",
    tag = ModuleTag.MOVEMENT,
) {
    private var delay by setting("Delay", 500, 50..10000, 10)
    private val shiftVelocity by setting("Shift velocity", true)
    private val requiresAura by setting("Requires Aura", false)

    private val isActive get() = (KillAura.isEnabled && KillAura.target != null) || !requiresAura

    private var packetPool = ConcurrentLinkedDeque<ServerPacket>()
    private var lastVelocity: EntityVelocityUpdateS2CPacket? = null
    private var lastUpdate = 0L

    private var box = DynamicAABB()
    private var lastBox = Box(BlockPos.ORIGIN)

    init {
        tickedRenderer("Blink Ticked Renderer") {
            val time = System.currentTimeMillis()

            if (isActive && time - lastUpdate < delay) return@tickedRenderer
            lastUpdate = time

            runSafe { poolPackets() }
        }

        immediateRenderer("Blink Immediate Renderer") {
            val color = ClickGuiLayout.primaryColor
            box(box.update(lastBox).box(mc.tickDelta) ?: return@immediateRenderer) {
                colors(color.setAlpha(0.3), color)
            }
        }

        listen<PacketEvent.Send.Pre> { event ->
            if (!isActive) return@listen

            packetPool.add(event.packet)
            event.cancel()
            return@listen
        }

        listen<PacketEvent.Send.Post> { event ->
            val packet = event.packet
            if (packet !is PlayerMoveC2SPacket) return@listen

            val vec = Vec3d(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0))
            if (vec == Vec3d.ZERO) return@listen

            lastBox = player.boundingBox.offset(vec - player.pos)
        }

        listen<PacketEvent.Receive.Pre> { event ->
            if (!isActive || !shiftVelocity) return@listen

            if (event.packet !is EntityVelocityUpdateS2CPacket) return@listen
            if (event.packet.entityId != player.id) return@listen

            lastVelocity = event.packet
            event.cancel()
            return@listen
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
