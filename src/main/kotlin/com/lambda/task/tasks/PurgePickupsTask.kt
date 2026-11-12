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
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PickupSlotKind
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.manager.managers.rotating.RotationRequest
import com.lambda.task.Task
import com.lambda.task.TaskMarker

@TaskMarker
context(automated: Automated)
fun purgePickups(
	selection: StackSelection = StackSelection.EVERYTHING,
	mergeHandling: MergeHandling = MergeHandling.DropDelta,
	rotation: (SafeContext.() -> RotationRequest)? = null
) = PurgePickupsTask(selection, mergeHandling, rotation, automated)

enum class MergeHandling {
	Ignore,
	Drop,
	DropDelta
}

/**
 * Drops every pickup matching [selection] until cancelled.
 */
class PurgePickupsTask @TaskMarker internal constructor(
	private val selection: StackSelection = StackSelection.EVERYTHING,
	private val mergeHandling: MergeHandling = MergeHandling.DropDelta,
	private val rotation: (SafeContext.() -> RotationRequest)? = null,
	automated: Automated
) : Task<Unit>(), Automated by automated {
	override val name = "Purging pickups"

	private val landing = linkedMapOf<SlotIdentity, DropTarget>()

	init {
		listen<InventoryEvent.PickupLanding> { event ->
			if (event.kind != PickupSlotKind.Empty || !selection.matches(event.stack)) return@listen
			landing[event.slot] = DropTarget(event.slot, event.stack, whole = true)
		}

		listen<TickEvent.Pre> {
			if (landing.isEmpty()) return@listen
			InventoryEvent.DropProgress(
				null,
				null,
				landing.values.toList(),
				0,
				this@PurgePickupsTask
			).post()
		}

		listen<InventoryEvent.Pickup> { event ->
			val sawLanding = event.slots.keys.count { landing.remove(it) != null } > 0
			if (!sawLanding && event.age >= age) return@listen
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
				drop(targets, rotation)
					.start(pauseParent = false)
			}
		}
	}
}
