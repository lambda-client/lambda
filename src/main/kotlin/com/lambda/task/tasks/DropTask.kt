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
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.mutate
import com.lambda.interaction.handler.handlers.ContainerHandler.findSlots
import com.lambda.interaction.handler.handlers.PacketLimitHandler.availablePackets
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.manager.managers.inventory.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.TaskMarker
import com.lambda.threading.withSafeAutomated
import com.lambda.util.item.StackMovePlanner
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

@TaskMarker
context(automated: Automated)
fun drop(
	selection: StackSelection = StackSelection.EVERYTHING,
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
) = DropTask(selection, containerSelection, emptyList(), automated)

@TaskMarker
@JvmName("dropSelection")
context(automated: Automated)
fun StackSelection.drop(
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
) = drop(this, containerSelection)

@TaskMarker
context(automated: Automated)
fun drop(targets: Iterable<DropTarget>) =
	DropTask(null, ContainerSelection.ACCESSED, targets.toList(), automated)

data class DropTarget(
	val slot: SlotIdentity,
	val expected: ItemStack,
	val amount: Int = expected.count,
	val whole: Boolean = false,
	val waitForResolution: Boolean = true,
)

data class DropResult(val dropped: Map<Item, Int>)

class DropTask @TaskMarker internal constructor(
	private val stackSelection: StackSelection?,
	private val containerSelection: ContainerSelection?,
	private val targets: List<DropTarget>,
	automated: Automated,
) : Task<DropResult>(), Automated by automated {
	override val name =
		if (stackSelection != null) "Dropping $stackSelection"
		else "Dropping ${targets.size} target(s)"

	private data class PendingDrop(
		val stack: ItemStack,
		var amount: Int,
		val whole: Boolean,
		val waitForResolution: Boolean,
	)

	private data class PlannedDrop(
		val slot: SlotIdentity,
		val entry: PendingDrop,
		val stack: ItemStack,
		val before: Int,
		val actions: List<DropAction>,
	)

	private data class DropAction(
		val slotId: Int,
		val button: Int,
		val type: SlotActionType,
	)

	private val drops = linkedMapOf<SlotIdentity, PendingDrop>()
	private val droppedTypes = mutableMapOf<Item, Int>()

	init {
		listen<TickEvent.Pre> {
			withSafeAutomated {
				tick()
				InventoryEvent.DropProgress(
					stackSelection,
					containerSelection,
					targets,
					droppedTypes.values.sum(),
					this@DropTask,
				).post()
			}
		}
	}

	override fun SafeContext.onStart() {
		targets.forEach { enqueue(it) }

		stackSelection?.let { selection ->
			containerSelection?.let {
				val slots =
					findSlots(
						stackSelection = selection.mutate { notEmpty() },
						containerSelection = containerSelection,
					).orEmpty()

				var remaining =
					if (selection.isUnbounded) Int.MAX_VALUE
					else selection.count
				slots.forEach { slot ->
					if (remaining <= 0) {
						finishIfDone()
						return
					}
					val stack = slot.stack
					if (stack.isEmpty) return@forEach
					val identity = slot.identity() ?: return@forEach

					val available = stack.count
					val requested =
						if (selection.alwaysWholeStacks) available
						else minOf(available, remaining)
					if (requested <= 0) return@forEach

					enqueue(
						identity = identity,
						amount = requested,
						expected = stack,
						whole = selection.isUnbounded || selection.alwaysWholeStacks,
						waitForResolution = false,
					)
					remaining -= requested
				}
			}
		}

		finishIfDone()
	}

	private fun AutomatedSafeContext.tick() {
		if (drops.isEmpty()) {
			finishIfDone()
			return
		}
		if (!player.currentScreenHandler.cursorStack.isEmpty) return

		val budget = availablePackets(PacketType.Inventory)
		if (budget <= 0) return

		val plans = planDrops(budget)
		if (plans.isEmpty()) {
			finishIfDone()
			return
		}

		inventoryRequest {
			plans.forEach { plan ->
				plan.actions.forEach { click(it.slotId, it.button, it.type) }
			}
			onComplete {
				record(plans)
				finishIfDone()
			}
		}.submit()
	}

	private fun enqueue(target: DropTarget) =
		enqueue(
			identity = target.slot,
			amount = target.amount,
			expected = target.expected,
			whole = target.whole,
			waitForResolution = target.waitForResolution,
		)

	private fun enqueue(
		identity: SlotIdentity,
		amount: Int,
		expected: ItemStack,
		whole: Boolean,
		waitForResolution: Boolean,
	) {
		if (!whole && amount <= 0) return

		val pending = PendingDrop(expected.copy(), amount, whole, waitForResolution)
		val existing = drops[identity]
		when {
			existing == null -> drops[identity] = pending
			whole -> drops[identity] = pending
			existing.whole -> {}
			existing.stack.sameItemAs(expected) -> existing.amount += amount
			else -> drops[identity] = pending
		}
	}

	private fun AutomatedSafeContext.planDrops(budget: Int): List<PlannedDrop> {
		val plans = mutableListOf<PlannedDrop>()
		var remaining = budget
		val iterator = drops.entries.iterator()

		while (iterator.hasNext() && remaining > 0) {
			val (identity, pending) = iterator.next()
			val slot = identity.resolve()
			if (slot == null) {
				if (!pending.waitForResolution) iterator.remove()
				continue
			}

			val stack = slot.stack
			if (stack.isEmpty || !pending.stack.sameItemAs(stack)) {
				iterator.remove()
				continue
			}

			if (pending.whole) {
				plans += PlannedDrop(
					slot = identity,
					entry = pending,
					stack = stack.copy(),
					before = stack.count,
					actions = listOf(DropAction(slot.id, 1, SlotActionType.THROW)),
				)
				remaining--
				continue
			}

			val amount = minOf(pending.amount, stack.count)
			if (amount <= 0) {
				iterator.remove()
				continue
			}

			val actions = planAmount(slot, stack, amount, remaining)
			plans +=
				PlannedDrop(
					slot = identity,
					entry = pending,
					stack = stack.copy(),
					before = stack.count,
					actions = actions,
				)
			remaining -= actions.size
		}

		return plans
	}

	private fun planAmount(slot: Slot, stack: ItemStack, amount: Int, budget: Int): List<DropAction> {
		if (amount >= stack.count) {
			return listOf(DropAction(slot.id, 1, SlotActionType.THROW))
		}

		val plan =
			StackMovePlanner.planDropWithin(
				state = StackMovePlanner.State(cursor = 0, source = stack.count, destination = 0),
				maxCount = slot.getMaxItemCount(stack),
				amount = amount,
				clickBudget = budget,
			)

		if (plan != null && plan.dropped > plan.clicks.size) {
			return plan.clicks.map { click ->
				when (click.target) {
					StackMovePlanner.Target.Source ->
						DropAction(slot.id, click.button.id, SlotActionType.PICKUP)

					StackMovePlanner.Target.Drop ->
						DropAction(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, click.button.id, SlotActionType.PICKUP)

					StackMovePlanner.Target.Destination ->
						error("Drop plans never touch a destination slot")
				}
			}
		}

		val singles = minOf(amount, budget)
		return if (singles <= 0) emptyList()
		else List(singles) { DropAction(slot.id, 0, SlotActionType.THROW) }
	}

	private fun SafeContext.record(plans: List<PlannedDrop>) {
		plans.forEach { plan ->
			val current = plan.slot.resolve()?.stack
			val same = current != null && !current.isEmpty && current.sameItemAs(plan.stack)
			val after = if (same) current.count else 0
			val dropped = (plan.before - after).coerceAtLeast(0)
			if (dropped > 0) droppedTypes.merge(plan.stack.item, dropped, Int::plus)

			val entry = drops[plan.slot]
			if (entry !== plan.entry) return@forEach

			if (entry.whole) {
				if (same && after > 0) entry.amount = after
				else drops.remove(plan.slot)
				return@forEach
			}

			entry.amount -= dropped
			if (entry.amount <= 0 || !same) drops.remove(plan.slot)
		}
	}

	private fun finishIfDone() {
		if (drops.isNotEmpty()) return
		success(DropResult(droppedTypes.toMap()))
	}

	private fun ItemStack.sameItemAs(other: ItemStack) =
		ItemStack.areItemsAndComponentsEqual(this, other)
}
