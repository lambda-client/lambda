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

import com.lambda.context.Automated
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PickupSlotKind
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder

@Ta5kBuilder
context(automated: Automated)
fun purgePickups(
	selection: StackSelection = StackSelection.EVERYTHING,
	mergeHandling: MergeHandling = MergeHandling.DropDelta,
) = PurgePickupsTask(selection, mergeHandling, automated)

enum class MergeHandling {
	Ignore,
	Drop,
	DropDelta
}

/**
 * Drops every pickup matching [selection] until cancelled.
 */
class PurgePickupsTask @Ta5kBuilder internal constructor(
	private val selection: StackSelection,
	private val mergeHandling: MergeHandling,
	automated: Automated
) : Task<Unit>(), Automated by automated {
	override val name = "Purging pickups"

	init {
		listen<InventoryEvent.Pickup> { event ->
			if (!selection.matches(event.stack)) return@listen
			val targets =
				event.slots.mapNotNull { (slot, landed) ->
					when {
						landed.kind == PickupSlotKind.Empty -> DropTarget(slot, landed.stack, whole = true)
						mergeHandling == MergeHandling.Drop -> DropTarget(slot, landed.stack, whole = true)
						mergeHandling == MergeHandling.DropDelta -> DropTarget(slot, landed.stack, amount = landed.amount)
						else -> null
					}
				}
			if (targets.isNotEmpty()) {
				drop(targets)
					.start(pauseParent = false)
			}
		}
	}
}
