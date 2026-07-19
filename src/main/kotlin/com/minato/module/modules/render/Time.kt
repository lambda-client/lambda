
package com.minato.module.modules.render

import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
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