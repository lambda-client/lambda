package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.PacketUtils.sendPacketSilently
import kotlinx.coroutines.delay
import net.minecraft.network.ClientConnection
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object TickShift : Module(
    name = "TickShift",
    description = "Smort tickshift for smort anticheats",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val maxBalance by setting("Max Balance", 20, 3..400, 1)
    private val boostAmount by setting("Boost", 3.0, 1.1..20.0, 0.01)
    private val slowdown by setting("Slowdown", 0.35, 0.01..0.9, 0.01)
    private val shiftVelocity by setting("Shift velocity", true)
    private val requiresAura by setting("Requires Aura", false)

    private val isActive get() = (KillAura.isEnabled && KillAura.target != null) || !requiresAura

    private var pingPool = ArrayDeque<CommonPongC2SPacket>()
    private var lastVelocity: EntityVelocityUpdateS2CPacket? = null

    private var balance = 0
    private var boost = false

    init {
        listener<TickEvent.Post> {
            if (Blink.isEnabled) {
                this@TickShift.info("TickShift is incompatible with blink")
                disable()
            }

            if (--balance <= 0) {
                balance = 0
                boost = false
                poolPackets()
            }
        }

        runConcurrent {
            while (true) {
                delay(50)

                if (++balance >= maxBalance) {
                    balance = maxBalance
                    boost = isActive
                }
            }
        }

        listener<ClientEvent.Timer> {
            if (!isActive) return@listener
            it.speed = if (boost) boostAmount else slowdown
        }

        listener<PacketEvent.Send.Pre> { event ->
            if (!isActive) return@listener
            if (event.packet !is CommonPongC2SPacket) return@listener

            pingPool.add(event.packet)
            event.cancel()
            return@listener
        }

        listener<PacketEvent.Receive.Pre> { event ->
            if (!isActive || !shiftVelocity) return@listener

            if (event.packet !is EntityVelocityUpdateS2CPacket) return@listener
            if (event.packet.id != player.id) return@listener

            lastVelocity = event.packet
            event.cancel()
            return@listener
        }

        onEnable {
            balance = 0
            boost = false

            pingPool.clear()
            lastVelocity = null
        }

        onDisable {
            poolPackets()
        }
    }
    
    private fun SafeContext.poolPackets() {
        while (pingPool.isNotEmpty()) {
            connection.sendPacketSilently(pingPool.removeFirst())
        }

        lastVelocity?.let { velocity ->
            ClientConnection.handlePacket(velocity, connection.connection.packetListener)
            lastVelocity = null
        }
    }
}