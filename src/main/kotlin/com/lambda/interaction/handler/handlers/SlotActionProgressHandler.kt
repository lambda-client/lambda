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

import com.lambda.context.Automated
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.SlotPrediction
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackAndSlot
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainers
import com.lambda.task.Task
import com.lambda.task.tasks.DropTarget
import com.lambda.util.item.ItemStackUtils.hasSpace
import com.lambda.util.item.ItemStackUtils.itemAndComponentsEqual
import com.lambda.util.item.ItemStackUtils.spaceLeft
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object SlotActionProgressHandler : Loadable {
	private const val CACHE_TICKS = 1

	private sealed interface Activity {
		val tick: Int
		val owner: Any?

		fun removesFrom(container: Container, slot: Slot): Boolean
		fun addsTo(container: Container, slot: Slot): Boolean

		context(_: Automated)
		fun apply(stacks: MutableMap<SlotIdentity, ItemStack>)
	}

	private class DropActivity(
		override val tick: Int,
		override val owner: Any?,
		private val targets: List<DropTarget>,
		private val stackSelection: StackSelection?,
		private val containerSelection: ContainerSelection?,
		private val progress: Int,
	) : Activity {
		override fun removesFrom(container: Container, slot: Slot): Boolean {
			val slotIdentity = slot.identity()
			if (slotIdentity != null && targets.any { it.slot == slotIdentity }) return true

			val stacks = stackSelection ?: return false
			val containers = containerSelection ?: return false
			return containers.matches(container) && stacks.matches(slot)
		}

		override fun addsTo(container: Container, slot: Slot) = false

		context(_: Automated)
		override fun apply(stacks: MutableMap<SlotIdentity, ItemStack>) {
			var dropped = progress
			targets.forEach { target ->
				val planned = if (target.whole) target.expected.count else target.amount
				val done = minOf(dropped, planned)
				dropped -= done
				if (done >= planned) return@forEach
				val held = stacks[target.slot] ?: target.slot.resolveStack() ?: return@forEach
				stacks[target.slot] =
					if (target.whole) ItemStack.EMPTY
					else held.without(planned - done)
			}

			val selection = stackSelection ?: return
			val containers = containerSelection ?: return
			stacks.take(selection, findContainers(containers), dropped)
		}
	}

	private class TransferActivity(
		override val tick: Int,
		override val owner: Any?,
		private val fromStack: StackSelection,
		private val toStack: StackSelection,
		private val fromSelection: ContainerSelection,
		private val toSelection: ContainerSelection,
		private val progress: Int,
	) : Activity {
		override fun removesFrom(container: Container, slot: Slot): Boolean =
			fromSelection.matches(container) && fromStack.matches(slot)

		override fun addsTo(container: Container, slot: Slot): Boolean =
			toSelection.matches(container) && toStack.matches(slot)

		context(_: Automated)
		override fun apply(stacks: MutableMap<SlotIdentity, ItemStack>) {
			val destinations = findContainers(toSelection)
			val sources = findContainers(fromSelection).filter { it !in destinations }
			stacks
				.take(fromStack, sources, progress)
				.forEach { incoming ->
					var left = incoming.count
					listOf(true, false).forEach { merge ->
						destinations.forEach { container ->
							container.slots.forEach { slot ->
								if (left <= 0) return@forEach
								val identity = slot.identity() ?: return@forEach
								val held = stacks[identity] ?: slot.stack
								val fits =
									if (merge) !held.isEmpty && held.hasSpace && held.itemAndComponentsEqual(incoming)
									else held.isEmpty && slot.canInsert(incoming)
								if (!fits || !toStack.matches(held, slot)) return@forEach
								val amount = minOf(left, if (merge) held.spaceLeft else incoming.maxCount)
								stacks[identity] =
									if (held.isEmpty) incoming.copyWithCount(amount)
									else held.copyWithCount(held.count + amount)
								left -= amount
							}
						}
					}
				}
		}
	}

	private val Task<*>.isFinished get() = state != Task.State.Running && state != Task.State.Paused

	private var tick = 0
	private val activities = mutableListOf<Activity>()

	init {
		listen<TickEvent.Pre>({ Int.MIN_VALUE }) {
			tick++
			activities.removeAll { it.tick < tick - CACHE_TICKS }
		}

		listen<InventoryEvent.DropProgress> { event ->
			track(
				DropActivity(
					tick,
					event.owner,
					event.targets,
					event.stackSelection,
					event.containerSelection,
					event.progress,
				)
			)
		}

		listen<InventoryEvent.ContainerTransferProgress> { event ->
			track(
				TransferActivity(
					tick,
					event.owner,
					event.fromStack,
					event.toStack,
					event.fromSelection,
					event.toSelection,
					event.progress,
				)
			)
		}

		listenUnsafe<WorldEvent.Leave> {
			activities.clear()
			tick = 0
		}
	}

	private fun track(activity: Activity) {
		activity.owner?.let { owner -> activities.removeAll { it.owner === owner } }
		activities += activity
	}

	fun isPullHandled(
		containers: Iterable<Container>,
		selection: StackSelection = StackSelection.EVERYTHING,
		owner: Any? = null
	): Boolean = isHandled(containers, selection, owner, adding = true)

	fun isPushHandled(
		containers: Iterable<Container>,
		owner: Any? = null
	): Boolean = isHandled(containers, null, owner, adding = false)

	private fun isHandled(
		containers: Iterable<Container>,
		selection: StackSelection?,
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
									(slot.stack.isEmpty || selection?.matches(slot) == true) &&
											activity.addsTo(container, slot)
								} else activity.removesFrom(container, slot)
							}
				}
	}

	context(_: Automated)
	fun predict(): SlotPrediction {
		val stacks = HashMap<SlotIdentity, ItemStack>()
		activities
			.filterNot { (it.owner as? Task<*>)?.isFinished == true }
			.forEach { it.apply(stacks) }
		return SlotPrediction(stacks)
	}

	private fun MutableMap<SlotIdentity, ItemStack>.take(
		selection: StackSelection,
		containers: List<Container>,
		done: Int,
	): List<ItemStack> {
		var left =
			if (selection.isUnbounded) Int.MAX_VALUE
			else (selection.count - done).coerceAtLeast(0)
		val taken = mutableListOf<ItemStack>()
		containers.forEach { container ->
			val stacksAndSlots = container.slots
				.map { StackAndSlot(it.identity()?.let(::get) ?: it.stack, it) }
			selection
				.filter(stacksAndSlots)
				.forEach {
					if (left <= 0) return taken
					val identity = it.slot?.identity() ?: return@forEach
					val held = this[identity] ?: it.stack
					if (held.isEmpty) return@forEach
					val amount =
						if (selection.alwaysWholeStacks) held.count
						else minOf(held.count, left)
					this[identity] = held.without(amount)
					taken += held.copyWithCount(amount)
					left -= amount
				}
		}
		return taken
	}

	private fun ItemStack.without(amount: Int): ItemStack =
		if (amount >= count) ItemStack.EMPTY else copyWithCount(count - amount)
}
