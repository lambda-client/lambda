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

package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.util.player.SlotUtils.matches
import net.minecraft.screen.slot.Slot

@Ta5kBuilder
fun slotByCache(slotCache: Slot) = SlotByCacheTask(slotCache)

class SlotByCacheTask @Ta5kBuilder internal constructor(
	val slotCache: Slot
) : Task<Slot>() {
	override val name = "Finding slot by cache: $slotCache"

	override fun SafeContext.onStart() {
		val slots = player.currentScreenHandler.slots
		val slot = slots.find { it matches slotCache }
		if (slot != null) success(slot)
		else failure("Failed to find matching slot by cache: $slotCache")
	}
}