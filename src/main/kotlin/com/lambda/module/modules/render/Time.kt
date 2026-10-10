/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.render

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket

object Time : Module(
	name = "Time",
	description = "Changes the time of day",
	tag = ModuleTag.RENDER
) {
	private val time by setting("Time", 12000L, 0L..24000L, 100L)

	private var prevTime = 0L

	init {
		onEnable { prevTime = world.levelProperties.timeOfDay }
		onDisable { world.levelProperties.timeOfDay = prevTime }

		listen<TickEvent.Pre> {
			world.levelProperties.timeOfDay = time
		}

		listen<PacketEvent.Receive.Pre> { event ->
			if (event.packet !is WorldTimeUpdateS2CPacket) return@listen
			event.cancel()
		}
	}
}