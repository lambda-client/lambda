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

package com.lambda.interaction.handler.handlers

import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.task.tasks.DropTarget
import net.minecraft.screen.slot.Slot

object SlotActionProgressHandler : Loadable {
	private const val CACHE_TICKS = 1

	private sealed interface Activity {
		val tick: Int
		val owner: Any?

		fun removesFrom(container: Container, slot: Slot): Boolean
		fun addsTo(container: Container, slot: Slot): Boolean
	}

	private class DropActivity(
		override val tick: Int,
		override val owner: Any?,
		private val targets: List<DropTarget>,
		private val stackSelection: StackSelection?,
		private val containerSelection: ContainerSelection?,
	) : Activity {
		override fun removesFrom(container: Container, slot: Slot): Boolean {
			val slotIdentity = slot.identity()
			if (slotIdentity != null && targets.any { it.slot == slotIdentity }) return true

			val stacks = stackSelection ?: return false
			val containers = containerSelection ?: return false
			return containers.matches(container) && stacks.matches(slot)
		}

		override fun addsTo(container: Container, slot: Slot) = false
	}

	private class TransferActivity(
		override val tick: Int,
		override val owner: Any?,
		private val fromStack: StackSelection,
		private val toStack: StackSelection,
		private val fromSelection: ContainerSelection,
		private val toSelection: ContainerSelection,
	) : Activity {
		override fun removesFrom(container: Container, slot: Slot): Boolean =
			fromSelection.matches(container) && fromStack.matches(slot)

		override fun addsTo(container: Container, slot: Slot): Boolean =
			toSelection.matches(container) && toStack.matches(slot)
	}

	private var tick = 0
	private val activities = mutableListOf<Activity>()

	init {
		listen<TickEvent.Pre>({ Int.MIN_VALUE }) {
			tick++
			activities.removeAll { it.tick < tick - CACHE_TICKS }
		}

		listen<InventoryEvent.DropProgress> { event ->
			activities +=
				DropActivity(
					tick,
					event.owner,
					event.targets,
					event.stackSelection,
					event.containerSelection,
				)
		}

		listen<InventoryEvent.ContainerTransferProgress> { event ->
			activities +=
				TransferActivity(
					tick,
					event.owner,
					event.fromStack,
					event.toStack,
					event.fromSelection,
					event.toSelection,
				)
		}

		listenUnsafe<WorldEvent.Leave> {
			activities.clear()
			tick = 0
		}
	}

	fun isPullHandled(
		containers: Iterable<Container>,
		selection: StackSelection = StackSelection.EVERYTHING,
		owner: Any? = null,
	): Boolean = isHandled(containers, selection, owner, adding = true)

	fun isPushHandled(
		containers: Iterable<Container>,
		selection: StackSelection = StackSelection.EVERYTHING,
		owner: Any? = null,
	): Boolean = isHandled(containers, selection, owner, adding = false)

	private fun isHandled(
		containers: Iterable<Container>,
		selection: StackSelection,
		owner: Any?,
		adding: Boolean,
	): Boolean {
		if (activities.isEmpty()) return false

		val candidates = containers
			.flatMap { container -> container.slots.map { container to it } }
		return candidates.isNotEmpty() &&
				activities.any { activity ->
					(owner == null || activity.owner !== owner) &&
							candidates.any { (container, slot) ->
								if (adding) {
									(slot.stack.isEmpty || selection.matches(slot)) &&
											activity.addsTo(container, slot)
								} else activity.removesFrom(container, slot)
							}
				}
	}
}
