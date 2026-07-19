
package com.minato.module.modules.player

import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.TickTimer

@Suppress("unused")
object InventoryResync : Module(
	name = "InventoryResync",
	description = "Resyncs your inventory, with a delay between resyncs",
	tag = ModuleTag.PLAYER,
	modulePriority = 10
) {
	private val delay by setting("Delay", 100, 0..1000, 5, unit = " ticks")

	private val timer = TickTimer()

	init {
		listen<TickEvent.Pre> {
			timer.tick()
			if (!timer.hasSurpassed(delay)) return@listen
			inventoryRequest {
				resyncInventory()
			}.submit()
			timer.reset()
		}
	}
}