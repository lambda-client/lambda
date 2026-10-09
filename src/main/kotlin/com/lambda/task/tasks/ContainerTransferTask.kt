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
import com.lambda.Lambda.LOG
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
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.PacketLimitHandler.availablePackets
import com.lambda.interaction.handler.handlers.ContainerHandler
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.handler.handlers.findContainers
import com.lambda.interaction.manager.managers.inventory.InvRequestFailureReason
import com.lambda.interaction.manager.managers.inventory.inventoryRequest
import com.lambda.module.modules.client.Client.verboseDebug
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.threading.runSafeAutomated
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.item.ItemStackUtils.hasSpace
import com.lambda.util.item.StackMovePlanner
import com.lambda.util.extension.containerSlots
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

@Ta5kBuilder
context(automated: Automated)
fun transfer(
	fromStack: StackSelection,
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.ANYTHING
) = ContainerTransferTask(fromStack, fromSelection, toSelection, toStack, automated)

@Ta5kBuilder
@JvmName("transferExt")
context(automated: Automated)
fun StackSelection.transfer(
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.ANYTHING
) = transfer(this, fromSelection, toSelection, toStack)

class TransferResult internal constructor(
	val stackSelection: StackSelection,
	val containerSelection: ContainerSelection
)

class ContainerTransferTask @Ta5kBuilder internal constructor(
	private val fromStack: StackSelection,
	private val fromSelection: ContainerSelection,
	private val toSelection: ContainerSelection,
	private val toStack: StackSelection,
	automated: Automated
) : Task<TransferResult>(), Automated by automated {
	override val name = "Transferring $fromStack"

	private val unlimited = fromStack.count <= 0

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
			if (verboseDebug) {
				LOG.info("[transfer] no source for $fromStack ${describeState()} fromSel=$fromSelection")
				findContainers(fromSelection).forEach {
					LOG.info("[transfer] candidate ${it.name} ${describeContainerState(it)} count=${it.count(fromStack.mutate(1))} slots=${describeSlots(it)}")
				}
			}
			fail("Could not find source containers for $fromStack (${describeState()})")
			return
		}

		toQueue += findContainers(toSelection).filter { canReceive(it) }
		if (verboseDebug) {
			LOG.info("[transfer] start fromStack=$fromStack from=${fromQueue.map { it.name to it.isAccessible }} to=${toQueue.map { it.name to it.isAccessible }} screen=${player.currentScreenHandler.syncId}")
			fromQueue.forEach { LOG.info("[transfer] from ${it.name} ${describeContainerState(it)} slots=${describeSlots(it)} stacks=${describeStacks(it)}") }
			toQueue.forEach { LOG.info("[transfer] to ${it.name} space=${it.spaceLeft(fromStack.mutate(1))} ${describeContainerState(it)} slots=${describeSlots(it)}") }
		}
		if (toQueue.isEmpty()) {
			fail("Could not find destination containers with space for $fromStack (${describeState()})")
			return
		}

		nextStep()
	}

	private fun SafeContext.describeState() =
		"fromQueue=${fromQueue.size} toQueue=${toQueue.size} " +
			"cursor=${player.currentScreenHandler.cursorStack} screen=${player.currentScreenHandler.syncId}"

	private fun describeSlots(container: Container): String {
		return try {
			container.slots.joinToString { slot ->
				"[id=${slot.id} idx=${slot.index} ${slot.stack.item} x${slot.stack.count} '${slot.stack.name.string}']"
			}
		} catch (_: Exception) {
			"unreadable"
		}
	}

	private fun SafeContext.describeContainerState(container: Container): String {
		return try {
			val handler = player.currentScreenHandler
			val handlerSlots = handler.slots.size
			val containerSlots = try {
				handler.containerSlots.size
			} catch (_: Exception) {
				-1
			}
			val last = ContainerHandler.lastInteractedBlockEntity
			"isAccessed=${container.isAccessed} handlerSync=${handler.syncId} handlerType=${handler.type} slots=$handlerSlots containerSlots=$containerSlots last=${last?.javaClass?.simpleName}@${last?.pos}"
		} catch (e: Exception) {
			"state-unreadable ${e.message}"
		}
	}

	private fun describeStacks(container: Container): String {
		return try {
			container.stacks.mapIndexed { index, stack ->
				"[$index ${stack.item} x${stack.count} '${stack.name.string}']"
			}.joinToString()
		} catch (_: Exception) {
			"unreadable"
		}
	}

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

	private fun canReceive(container: Container) =
		container.spaceLeft(fromStack.mutate(1)) > 0 ||
			container.count(toStack.mutate { notEmpty(); withoutSelection(fromStack) }) > 0

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
			taskOrNull { to.access() }.then { toContext ->
				MoveTask(
					fromContainers = listOf(from),
					toContainers = toContainers,
					fromSelection = fromStack,
					toSelection = toStack,
					limit = remaining
				).thenOrNull {
					toContainers
						.filter { !canReceive(it) }
						.forEach { full ->
							toQueue.removeAll { it.haveMatchingInventories(full) }
						}
					toContext?.close()
				}.thenOrNull {
					if (from.count(fromStack) <= 0) {
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
				MoveTask(
					fromContainers = listOf(from),
					toContainers = stagingContainers,
					fromSelection = fromStack,
					toSelection = StackSelection.ANYTHING,
					limit = pullLimit,
					allowReplace = false,
					trackResult = false
				).thenOrNull { pulled ->
					staged += pulled
					if (from.count(fromStack) <= 0) {
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
				MoveTask(
					fromContainers = stagingContainers,
					toContainers = listOf(to),
					fromSelection = fromStack,
					toSelection = toStack,
					limit = pushLimit
				).thenOrNull { pushed ->
					staged = (staged - pushed).coerceAtLeast(0)
					if (pushed <= 0 || !canReceive(to)) {
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
		private val limit: Int = if (unlimited) Int.MAX_VALUE else remaining,
		private val allowReplace: Boolean = true,
		private val trackResult: Boolean = true
	) : Task<Int>() {
		override val name get() = "Moving ${this@MoveTask.fromSelection}"
		private var moved = 0

		private val toSelection =
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
					if (limit == Int.MAX_VALUE) 0
					else minOf(currentLimit, fromSelection.count.coerceAtLeast(1))
				) { notEmpty() }

			// Find the best slot pair fresh — never cached across ticks
			val pair =
				fromContainers.firstNotNullOfOrNull { fromContainer ->
					val fromSlot = fromContainer.findSlot(currentSelection) ?: return@firstNotNullOfOrNull null

					toContainers.firstNotNullOfOrNull { toContainer ->
						if (toContainer.haveMatchingInventories(fromContainer)) return@firstNotNullOfOrNull null
						if (!fromContainer.canSwapWith(toContainer)) return@firstNotNullOfOrNull null
						findToSlot(fromSlot, toContainer)?.let { toSlot ->
							SlotPair(fromContainer, fromSlot, toContainer, toSlot)
						}
					}
				}

			if (pair == null) {
				if (verboseDebug) {
					val fromSeen = fromContainers.map { it.name to (it.findSlot(currentSelection) != null) }
					val cursorStack = CursorContainer.stacks.firstOrNull() ?: ItemStack.EMPTY
					LOG.info("[transfer] no pair for $currentSelection from=$fromSeen to=${toContainers.map { it.name }} cursor=$cursorStack")
					fromContainers.forEach { LOG.info("[transfer] dump from ${it.name} ${describeContainerState(it)} slots=${describeSlots(it)} stacks=${describeStacks(it)}") }
					toContainers.forEach { LOG.info("[transfer] dump to ${it.name} ${describeContainerState(it)} canSwap=${fromContainers.firstOrNull()?.canSwapWith(it)} slots=${describeSlots(it)}") }
				}
				finish()
				return true
			}

			return move(pair, atMost = currentLimit)
		}

		private fun AutomatedSafeContext.findToSlot(fromSlot: Slot, toContainer: Container): Slot? {
			val stack = fromSlot.stack
			val partialStack =
				stackSelection {
					predicate { candidate, _ -> !candidate.isEmpty && candidate.hasSpace && candidate.sameItemAs(stack) }
					canInsert(stack)
				}
			return toContainer.findSlot(partialStack)
				?: toContainer.findReplaceSlot(stack, stackSelection { isEmpty() })
				?: findSwappableSlot(fromSlot, toContainer)
		}

		private fun AutomatedSafeContext.findSwappableSlot(fromSlot: Slot, toContainer: Container): Slot? {
			if (!allowReplace) return null
			return toContainer.findReplaceSlot(fromSlot.stack, toSelection)?.takeIf { fromSlot.canInsert(it.stack) }
		}

		private fun AutomatedSafeContext.move(pair: SlotPair, atMost: Int): Boolean {
			val stack = pair.fromSlot.stack
			val count = minOf(stack.count, atMost)
			val cursor = CursorContainer.stacks.firstOrNull() ?: ItemStack.EMPTY
			return when {
				pair.toSlot.stack.isForeignTo(stack) -> swapOut(pair)
				count == stack.count && cursor.isEmpty && quickMoveStaysInside(pair.fromContainer, stack) -> quickMoveWhole(pair)
				else -> clickThrough(pair, count)
			}
		}

		private fun AutomatedSafeContext.swapOut(pair: SlotPair): Boolean {
			val count = pair.fromSlot.stack.count
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

			val left = pair.fromSlot.stack.takeIf { it.sameItemAs(before) }?.count ?: 0
			val count = before.count - left
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
			// A foreign stack on the cursor is somebody else's business; wait until it has been put away.
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
				// Even an untouched budget cannot fit the shortest plan: this pair can never be served, give it up.
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
