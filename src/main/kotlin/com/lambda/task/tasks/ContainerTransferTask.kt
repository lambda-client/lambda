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
import com.lambda.event.events.ContainerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ExternalContainer
import com.lambda.interaction.container.containers.CursorContainer
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.mutate
import com.lambda.interaction.container.selection.selectContainers
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainers
import com.lambda.interaction.handler.handlers.PacketLimitHandler.availablePackets
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.manager.managers.inventory.InvRequestFailureReason
import com.lambda.interaction.manager.managers.inventory.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.threading.runSafeAutomated
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.item.ItemStackUtils.hasSpace
import com.lambda.util.item.StackMovePlanner
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

@Ta5kBuilder
context(automated: Automated)
fun transfer(
	fromStack: StackSelection,
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.EVERYTHING,
	allowQuickMove: Boolean = true,
) = ContainerTransferTask(fromStack, fromSelection, toSelection, toStack, allowQuickMove, automated)

@Ta5kBuilder
@JvmName("transferExt")
context(automated: Automated)
fun StackSelection.transfer(
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.EVERYTHING,
	allowQuickMove: Boolean = true,
) = transfer(this, fromSelection, toSelection, toStack, allowQuickMove)

class TransferResult internal constructor(
	val stackSelection: StackSelection,
	val containerSelection: ContainerSelection
)

class ContainerTransferTask @Ta5kBuilder internal constructor(
	private val fromStack: StackSelection,
	private val fromSelection: ContainerSelection,
	private val toSelection: ContainerSelection,
	private val toStack: StackSelection,
	private val allowQuickMove: Boolean,
	automated: Automated
) : Task<TransferResult>(), Automated by automated {
	override val name = "Transferring $fromStack"

	private val unlimited = fromStack.isUnbounded

	private var transferred = 0
	private val remaining
		get() =
			if (unlimited) Int.MAX_VALUE
			else (fromStack.count - transferred).coerceAtLeast(0)
	private val isComplete
		get() = !unlimited && remaining <= 0

	private var staged = 0

	private var lastSource: Container? = null

	private val progressText
		get() = "$transferred moved"

	private val resultSlots = mutableMapOf<Slot, ItemStack>()
	private val resultContainers = mutableSetOf<Container>()

	private val fromQueue = mutableListOf<Container>()
	private val toQueue = mutableListOf<Container>()

	private val stagingContainers: List<Container>
		get() = listOf(InventoryContainer, HotbarContainer)
			.filter { it.isAccessible }

	private val pullLimit
		get() =
			if (unlimited) Int.MAX_VALUE
			else (remaining - staged).coerceAtLeast(0)

	private val pushLimit
		get() =
			if (unlimited) staged
			else minOf(remaining, staged)

	override fun SafeContext.onStart() {
		fromQueue +=
			findContainers(
				fromSelection.mutate {
					hasStack(fromStack.mutate(1))
				}
			)
		if (fromQueue.isEmpty() || (!unlimited && !(fromStack isIn fromQueue))) {
			fail("Could not find source containers for $fromStack (${describeState()})")
			return
		}

		toQueue += findContainers(toSelection).filter { canReceive(it) }
		if (toQueue.isEmpty()) {
			fail("Could not find destination containers with space for $fromStack (${describeState()})")
			return
		}

		nextStep()
	}

	private fun SafeContext.describeState() =
		"fromQueue=${fromQueue.size} toQueue=${toQueue.size} " +
			"cursor=${player.currentScreenHandler.cursorStack} screen=${player.currentScreenHandler.syncId}"

	private fun SafeContext.nextStep() {
		if (isComplete) {
			success(buildResult())
			return
		}

		val from = fromQueue.firstOrNull()
			?: run {
				when {
					unlimited && transferred > 0 -> success(buildResult())
					toQueue.isEmpty() -> fail("No destination container has space left for $fromStack ($progressText, ${describeState()})")
					else -> fail("Ran out of source containers for $fromStack ($progressText, ${describeState()})")
				}
				return
			}
		val to = toQueue.firstOrNull { !it.haveMatchingInventories(from) }
			?: toQueue.firstOrNull { it !is ExternalContainer }
			?: run {
				fail("No destination container has space left for $fromStack ($progressText, ${describeState()})")
				return
			}
		lastSource = from

		val prevTransferred = transferred
		val step =
			if (from is ExternalContainer && to is ExternalContainer) tripChain(from)
			else directChain(from, to)

		step
			.onSuccess {
				if (isComplete) {
					success(buildResult())
					return@onSuccess
				}

				if (transferred <= prevTransferred) {
					toQueue.removeAll { it.haveMatchingInventories(to) }
				}
				nextStep()
			}
			.start()
	}

	private fun buildResult() =
		TransferResult(
			fromStack.mutate {
				sortedWith {
					compareByDescending { stackAndSlot ->
						val slot = stackAndSlot.slot
						slot != null &&
							resultSlots.entries.find {
								slot.index == it.key.index
							}?.let { moveEntry ->
								slot.inventory::class == moveEntry.key.inventory::class &&
										moveEntry.key.stack.equal(moveEntry.value)
							} == true
					}
				}
			},
			selectContainers(
				containers = resultContainers.toTypedArray(),
				scope = toSelection.scope
			)
		)

	private fun directChain(from: Container, to: Container): Task<*> {
		val toContainers =
			if (to is ExternalContainer) listOf(to)
			else listOf(to) +
					toQueue.filter {
						it !== to && it !is ExternalContainer &&
								!it.haveMatchingInventories(from)
					}

		return taskOrNull { from.access() }.then { fromContext ->
			val fromContainer = fromContext?.container ?: from
			taskOrNull { to.access() }.then { toContext ->
				MoveTask(
					fromContainers = listOf(fromContainer),
					toContainers = toContext?.container?.let { listOf(it) } ?: toContainers,
					fromSelection = fromStack,
					toSelection = toStack,
					limit = remaining
				).thenOrNull {
					toContainers
						.filter { !canReceive(toContext?.container ?: it) }
						.forEach { full ->
							toQueue.removeAll { it.haveMatchingInventories(full) }
						}
					toContext?.close()
				}.thenOrNull {
					if (fromContainer.count(fromStack) <= 0) {
						fromQueue.removeAll { it.haveMatchingInventories(from) }
					}
					fromContext?.close()
				}
			}
		}
	}

	private fun tripChain(from: Container): Task<*> =
		taskOrNull { from.access() }
			.then { fromContext ->
				val fromContainer = fromContext?.container ?: from
				MoveTask(
					fromContainers = listOf(fromContainer),
					toContainers = stagingContainers,
					fromSelection = fromStack,
					toSelection = StackSelection.EVERYTHING,
					limit = pullLimit,
					allowReplace = false,
					trackResult = false
				).thenOrNull { pulled ->
					staged += pulled
					if (fromContainer.count(fromStack) <= 0) {
						fromQueue.removeAll { it.haveMatchingInventories(from) }
					}
					fromContext?.close()
				}
			}
			.then { pushChain(from) }

	private fun pushChain(from: Container): Task<*> {
		val to = toQueue.firstOrNull { !it.haveMatchingInventories(from) }
			?: return taskOrNull<Unit> { null }
		if (staged <= 0) return taskOrNull<Unit> { null }

		return taskOrNull { to.access() }
			.then { toContext ->
				val toContainer = toContext?.container ?: to
				MoveTask(
					fromContainers = stagingContainers,
					toContainers = listOf(toContainer),
					fromSelection = fromStack,
					toSelection = toStack,
					limit = pushLimit
				).thenOrNull { pushed ->
					staged = (staged - pushed).coerceAtLeast(0)
					if (pushed <= 0 || !canReceive(toContainer)) {
						toQueue.removeAll { it.haveMatchingInventories(to) }
					}
					toContext?.close()
				}
			}
			.thenOrNull {
				if (isComplete || staged <= 0) return@thenOrNull null

				if (toQueue.any { !it.haveMatchingInventories(from) }) pushChain(from)
				else null
			}
	}

	private fun canReceive(container: Container) =
		container.spaceLeft(fromStack.mutate(1)) > 0 ||
				container.count(toStack.mutate { notEmpty(); withoutSelection(fromStack) }) > 0

	private data class SlotPair(
		val fromContainer: Container,
		val fromSlot: Slot,
		val toContainer: Container,
		val toSlot: Slot
	) {
		operator fun get(target: StackMovePlanner.Target) =
			when (target) {
				StackMovePlanner.Target.Source -> fromSlot
				StackMovePlanner.Target.Destination -> toSlot
			}
	}

	private inner class MoveTask @Ta5kBuilder constructor(
		private val fromContainers: List<Container>,
		private val toContainers: List<Container>,
		private val fromSelection: StackSelection,
		toSelection: StackSelection,
		private val limit: Int,
		private val allowReplace: Boolean = true,
		private val trackResult: Boolean = true
	) : Task<Int>() {
		override val name get() = "Moving ${this@MoveTask.fromSelection}"
		private var moved = 0

		private val countsAir = fromSelection.countEmptyAsOne
		private val wholeStacks = fromSelection.alwaysWholeStacks

		private val baseToSelection = toSelection

		private val swappableSelection =
			toSelection.mutate {
				notEmpty()
				withoutSelection(this@MoveTask.fromSelection)
			}

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					do {
						val done = tick()
					} while (!done)
				}
			}
		}

		private fun AutomatedSafeContext.tick(): Boolean {
			val currentLimit =
				if (limit == Int.MAX_VALUE) Int.MAX_VALUE
				else (limit - moved).coerceAtLeast(0)
			if (currentLimit <= 0) {
				finish()
				return true
			}

			val currentSelection =
				fromSelection.mutate(
					if (wholeStacks || limit == Int.MAX_VALUE) StackSelection.UNBOUNDED
					else minOf(currentLimit, fromSelection.count.coerceAtLeast(1))
				) { if (!countsAir) notEmpty() }

			val pair =
				fromContainers.firstNotNullOfOrNull { fromContainer ->
					val fromSlot = fromContainer.findSlot(currentSelection) ?: return@firstNotNullOfOrNull null

					toContainers.firstNotNullOfOrNull { toContainer ->
						if (!fromContainer.canSwapWith(toContainer)) return@firstNotNullOfOrNull null
						findToSlot(fromSlot, toContainer)
							?.takeIf { !toContainer.haveMatchingInventories(fromContainer) || it.index != fromSlot.index }
							?.let { toSlot ->
								SlotPair(fromContainer, fromSlot, toContainer, toSlot)
							}
					}
				}

			if (pair == null) {
				finish()
				return true
			}

			return move(
				pair,
				if (wholeStacks) pair.fromSlot.stack.count(countsAir)
				else currentLimit
			)
		}

		private fun AutomatedSafeContext.findToSlot(fromSlot: Slot, toContainer: Container): Slot? {
			val stack = fromSlot.stack
			return toContainer.findSlot(
				baseToSelection.mutate {
					predicate { candidate, slot -> slot !== fromSlot && !candidate.isEmpty && candidate.hasSpace && candidate.sameItemAs(stack) }
					canInsert(stack)
				}
			) ?: toContainer.findReplaceSlot(
				stack,
				baseToSelection.mutate { isEmpty() }
			) ?: findSwappableSlot(fromSlot, toContainer)
		}

		private fun AutomatedSafeContext.findSwappableSlot(fromSlot: Slot, toContainer: Container): Slot? {
			if (!allowReplace) return null
			return toContainer.findReplaceSlot(fromSlot.stack, swappableSelection)?.takeIf { fromSlot.canInsert(it.stack) }
		}

		private fun AutomatedSafeContext.move(pair: SlotPair, atMost: Int): Boolean {
			val stack = pair.fromSlot.stack
			val size = stack.count(countsAir)
			val count = minOf(size, atMost)
			val cursor = CursorContainer.stacks.firstOrNull() ?: ItemStack.EMPTY
			if (pair.toSlot.stack.isForeignTo(stack)) {
				if (count != size) {
					finish()
					return true
				}
				return swapOut(pair)
			}
			if (stack.isEmpty) {
				recordMoved(size)
				finish()
				return true
			}
			return when (count) {
				size if cursor.isEmpty &&
						allowQuickMove &&
						quickMoveStaysInside(pair.fromContainer, stack) -> quickMoveWhole(pair)

				size if cursor.isEmpty &&
						pair.toSlot.stack.isEmpty -> swapOut(pair)

				else -> clickThrough(pair, count)
			}
		}

		private fun AutomatedSafeContext.swapOut(pair: SlotPair): Boolean {
			val count = pair.fromSlot.stack.count(countsAir)
			val request = pair.fromContainer.swapRequest(pair.fromSlot, pair.toSlot, pair.toContainer)?.submit()
			return when {
				request == null -> {
					finish()
					true
				}
				!request.done -> {
					finishUnlessTransient(request.failureReason)
					true
				}
				else -> {
					recordMoved(count)
					recordDestination(pair.toSlot, pair.toContainer)
					false
				}
			}
		}

		private fun AutomatedSafeContext.quickMoveWhole(pair: SlotPair): Boolean {
			if (availablePackets(PacketType.Inventory) < 1) return true
			val transferEvent =
				ContainerEvent.Transfer(
					pair.fromSlot,
					pair.toSlot,
					pair.fromContainer,
					pair.toContainer
				)
			if (transferEvent.post().isCanceled()) return true

			val before = pair.fromSlot.stack.copy()
			val destinationsBefore =
				if (trackResult) toContainers.map { container -> container.slots.map { it.stack.copy() } }
				else emptyList()
			val request = inventoryRequest { quickMove(pair.fromSlot.id) }.submit()
			if (!request.done) {
				finishUnlessTransient(request.failureReason)
				return true
			}

			val left = pair.fromSlot.stack.takeIf { it.sameItemAs(before) }?.count(countsAir) ?: 0
			val count = before.count(countsAir) - left
			if (count <= 0) {
				finish()
				return true
			}
			recordMoved(count)
			if (trackResult) recordReceivingSlots(destinationsBefore)
			return false
		}

		private fun AutomatedSafeContext.clickThrough(pair: SlotPair, count: Int): Boolean {
			if (!CursorContainer.isAccessible) {
				finish()
				return true
			}
			val stack = pair.fromSlot.stack
			val cursor = CursorContainer.stacks.firstOrNull() ?: return true
			if (cursor.isForeignTo(stack)) {
				finish()
				return true
			}

			val budget = availablePackets(PacketType.Inventory)
			val beforeTo = pair.toSlot.stack.count
			val plan =
				StackMovePlanner.planWithin(
					state = StackMovePlanner.State(cursor.count, stack.count, beforeTo),
					maxCount = pair.toSlot.getMaxItemCount(stack),
					goal = beforeTo + count,
					clickBudget = budget
				)
			if (plan == null) {
				if (budget >= buildConfig.inventoryLimit) finish()
				return true
			}

			val transferEvent =
				ContainerEvent.Transfer(
					pair.fromSlot,
					pair.toSlot,
					pair.fromContainer,
					pair.toContainer
				)
			if (transferEvent.post().isCanceled()) return true
			val request =
				inventoryRequest {
					plan.clicks.forEach { click -> pickup(pair[click.target].id, click.button.id) }
				}.submit()
			if (!request.done) {
				finishUnlessTransient(request.failureReason)
				return true
			}

			recordMoved(plan.destination - beforeTo)
			recordDestination(pair.toSlot, pair.toContainer)
			return false
		}

		private fun AutomatedSafeContext.quickMoveStaysInside(fromContainer: Container, stack: ItemStack): Boolean {
			if (stack.components.contains(DataComponentTypes.EQUIPPABLE)) return false
			val playerScreen = player.currentScreenHandler.syncId == 0
			fun covers(container: Container) = toContainers.any { it.haveMatchingInventories(container) }
			fun coversOpenContainer() = toContainers.any { it is ExternalContainer && it.isAccessed }
			return when {
				fromContainer is ExternalContainer -> covers(InventoryContainer) && covers(HotbarContainer)
				fromContainer === InventoryContainer -> if (playerScreen) covers(HotbarContainer) else coversOpenContainer()
				fromContainer === HotbarContainer -> if (playerScreen) covers(InventoryContainer) else coversOpenContainer()
				else -> false
			}
		}

		private fun recordReceivingSlots(before: List<List<ItemStack>>) {
			toContainers.zip(before).forEach { (container, previous) ->
				container.slots.forEachIndexed { index, slot ->
					if (slot.stack.grewFrom(previous.getOrNull(index))) recordDestination(slot, container)
				}
			}
		}

		private fun recordMoved(count: Int) {
			moved += count
			if (trackResult) transferred += count
		}

		private fun recordDestination(slot: Slot, container: Container) {
			if (!trackResult) return
			resultSlots[slot] = slot.stack.copy()
			resultContainers += container
		}

		private fun finishUnlessTransient(failureReason: InvRequestFailureReason) {
			if (failureReason == InvRequestFailureReason.None) finish()
		}

		private fun finish() {
			success(moved)
		}

		private fun ItemStack.sameItemAs(other: ItemStack) = ItemStack.areItemsAndComponentsEqual(this, other)

		private fun ItemStack.isForeignTo(other: ItemStack) = !isEmpty && !sameItemAs(other)

		private fun ItemStack.grewFrom(previous: ItemStack?) =
			!isEmpty && (previous == null || previous.isEmpty || !sameItemAs(previous) || count > previous.count)
	}

	private fun fail(message: String) {
		failure(FailedContainerTransferException(message, staged))
	}
}

@Suppress("unused")
class FailedContainerTransferException(
	message: String,
	val staged: Int
) : IllegalStateException(message)
