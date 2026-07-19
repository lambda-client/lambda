
package com.minato.module.modules.movement.elytrafly.modes

import com.minato.config.Config
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.minato.module.modules.movement.elytrafly.ElytraFlyMode

class GeneralElytraFly(override val c: Config) : ElytraFlyMode(FlyMode.General) {
	init {
		listen<TickEvent.Pre> {
			if (fakeGliding) flyOrFakeFly()
		}
	}
}