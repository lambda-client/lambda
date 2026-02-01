/*
 * Copyright 2025 Lambda
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
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import kotlinx.coroutines.delay
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object TickShift : Module(
	name = "TickShift",
	description = "Smort tickshift for smort anticheats",
	tag = ModuleTag.MOVEMENT,
) {
	val maxBalance by setting("Max Balance", 20, 3..400, 1)
	private val boostAmount by setting("Boost", 3.0, 1.1..20.0, 0.01)
	private val slowdown by setting("Slowdown", 0.35, 0.01..0.9, 0.01)
	private val delaySetting by setting("Delay", 0, 0..2000, 10)
	private val grim by setting("Grim", true)
	private val strictSetting by setting("Strict", true) { !grim }
	private val shiftVelocity by setting("Shift velocity", true) { grim }
	private val requiresAura by setting("Requires Aura", false)

	private val strict get() = grim || strictSetting

	val isActive: Boolean
		get() {
			if (requiresAura && (!KillAura.isEnabled || KillAura.target == null)) return false
			return System.currentTimeMillis() - lastBoost > delaySetting
		}

	private var pingPool = ArrayDeque<CommonPongC2SPacket>()
	private var lastVelocity: EntityVelocityUpdateS2CPacket? = null

	var balance = 0
	var boost = false
	private var lastBoost = 0L

	init {
		listen<TickEvent.Post> {
			if (Blink.isEnabled) {
				this@TickShift.info("TickShift is incompatible with blink")
				disable()
			}

			if (strict) balance--

			if (balance <= 0) {
				balance = 0
				poolPackets()

				if (boost) {
					boost = false
					lastBoost = System.currentTimeMillis()
				}
			}
		}

		listen<PacketEvent.Send.Pre> {
			if (it.packet !is PlayerMoveC2SPacket) return@listen
			if (!strict) balance--
		}

		runConcurrent {
			while (true) {
				delay(50)

				if (isEnabled) {
					if (++balance >= maxBalance) {
						balance = maxBalance
						boost = isActive
					}
				}
			}
		}

		listen<ClientEvent.TimerUpdate> {
			if (!isActive) {
				poolPackets()
				return@listen
			}

			it.speed = if (boost) boostAmount else slowdown
		}

		listen<PacketEvent.Send.Pre> { event ->
			if (!isActive || !grim || event.isCanceled()) return@listen
			if (event.packet !is CommonPongC2SPacket) return@listen

			pingPool.add(event.packet)
			event.cancel()
			return@listen
		}

		listen<PacketEvent.Receive.Pre> { event ->
			if (!isActive || !grim || !shiftVelocity || event.isCanceled()) return@listen

			if (event.packet !is EntityVelocityUpdateS2CPacket) return@listen
			if (event.packet.entityId != player.id) return@listen

			lastVelocity = event.packet
			event.cancel()
			return@listen
		}

		onEnable {
			balance = 0
			boost = false

			pingPool.clear()
			lastVelocity = null
		}

		onDisable {
			balance = 0
			boost = false

			poolPackets()
		}
	}

	private fun SafeContext.poolPackets() {
		while (pingPool.isNotEmpty()) {
			connection.sendPacketSilently(pingPool.removeFirst())
		}

		lastVelocity?.let {
			connection.handlePacketSilently(it)
			lastVelocity = null
		}
	}
}
