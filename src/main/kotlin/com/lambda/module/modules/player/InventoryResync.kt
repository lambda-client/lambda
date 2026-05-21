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

package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.TickTimer

@Suppress("unused")
object InventoryResync : Module(
	name = "InventoryResync",
	description = "Resyncs your inventory, with a delay between resyncs",
	tag = ModuleTag.Player,
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