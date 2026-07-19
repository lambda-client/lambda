
package com.minato.module.modules.player

import com.minato.event.events.PlayerEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag

@Suppress("unused")
object NoForceRotate : Module(
	name = "NoForceRotate",
	description = "Prevents the server from forcing your players rotation",
	tag = ModuleTag.PLAYER
) {
	init {
		listen<PlayerEvent.ServerForceRotate> { it.cancel() }
	}
}