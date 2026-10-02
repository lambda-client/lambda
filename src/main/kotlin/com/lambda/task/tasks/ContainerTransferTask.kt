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
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.mutate
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.mutate
import com.lambda.interaction.container.selection.selectContainers
import com.lambda.interaction.handler.handlers.PacketLimitHandler.availablePackets
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.handler.handlers.findContainers
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.interaction.manager.managers.inventory.InvRequestFailureReason
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.threading.runSafeAutomated
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.item.ItemStackUtils.hasSpace
import com.lambda.util.item.StackMovePlanner
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

/**
 * Moves items matching [fromStack] out of containers matching [fromSelection] into containers matching
 * [toSelection].
 *
 * Counting semantics: [StackSelection.count] of [fromStack] is the exact number of items to move. A count
 * of `0` or less moves everything that matches. Individual moves are planned to the exact item count using
 * left and right clicks (see [StackMovePlanner]); a whole stack is only moved past the requested count when
 * the sole way into the destination is swapping out a foreign stack.
 *
 * Destination slots are chosen in this order: a partial stack of the same item, an empty slot, then (only
 * when nothing else is possible) a foreign stack matching [toStack] that gets swapped into the source slot.
 *
 * When both containers are external (chests, shulker boxes, ender chest) items travel through the player's
 * hotbar and inventory: a trip pulls as much as fits (or as much as still needs moving) into free player
 * slots, closes the source, opens the destination and pushes exactly the staged items back out. Trips repeat
 * until the request is satisfied, so sources larger than the player inventory work too.
 */
class ContainerTransferTask @Ta5kBuilder internal constructor(
	private val fromStack: StackSelection,
	private val fromSelection: ContainerSelection,
	private val toSelection: ContainerSelection,
	private val toStack: StackSelection,
	automated: Automated
) : Task<TransferResult>(), Automated by automated {
	override val name = "Transferring $fromStack"

	/** `true` when [fromStack] carries no count: everything matching gets moved. */
	private val unlimited = fromStack.count <= 0

	private var transferred = 0
	private val remaining
		get() =
			if (unlimited) Int.MAX_VALUE
			else (fromStack.count - transferred).coerceAtLeast(0)
	private val isComplete
		get() = !unlimited && remaining <= 0

	private val progressText
		get() = "$transferred moved"

	private val resultSlots = mutableMapOf<Slot, ItemStack>()
	private val resultContainers = mutableSetOf<Container>()

	private val fromQueue = mutableListOf<Container>()
	private val toQueue = mutableListOf<Container>()

	override fun SafeContext.onStart() {
		fromQueue +=
			findContainers(
				fromSelection.mutate {
					hasStack(fromStack.mutate(1))
				}
			)
		if (fromQueue.isEmpty() || (!unlimited && !(fromStack isIn fromQueue))) {
			failure("Could not find source containers for $fromStack")
			return
		}

		toQueue += findContainers(toSelection).filter { canReceive(it) }
		if (toQueue.isEmpty()) {
			failure("Could not find destination containers with space for $fromStack")
			return
		}

		nextStep()
	}

	/**
	 * Picks the next source and destination pair and runs one transfer step between them. A step either moves
	 * items directly (at most one side external) or performs a pull/push trip through the player inventory
	 * (both sides external). Repeats until the requested count is reached or a queue runs dry.
	 */
	private fun SafeContext.nextStep() {
		if (isComplete) {
			success(buildResult())
			return
		}

		val from = fromQueue.firstOrNull()
			?: run {
				if (unlimited && transferred > 0) success(buildResult())
				else failure("Ran out of source containers for $fromStack ($progressText)")
				return
			}
		val to = toQueue.firstOrNull { !it.haveMatchingInventories(from) }
			?: run {
				failure("No destination container has space left for $fromStack ($progressText)")
				return
			}

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

	/**
	 * A destination can receive when it has room for the item (a partial stack or an empty slot) or holds a
	 * stack matching [toStack] that may be swapped out.
	 */
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

	/**
	 * Moves directly between [from] and [to]. At most one of them is external, so at most one screen has to be
	 * opened; screens this task opened are closed again afterwards.
	 */
	private fun directChain(from: Container, to: Container): Task<*> =
		taskOrNull { from.access() }.then { fromContext ->
			taskOrNull { to.access() }.then { toContext ->
				MoveTask(
					fromContainers = listOf(from),
					toContainers = listOf(to),
					fromSelection = fromStack,
					toSelection = toStack,
				).thenOrNull {
					if (!canReceive(to)) {
						toQueue.removeAll { it.haveMatchingInventories(to) }
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

	/**
	 * One trip for two external containers: pull from [from] into free player slots, close it, then push the
	 * staged items into the destination queue. Loops are driven naturally by [nextStep].
	 */
	private fun tripChain(from: Container): Task<*> =
		taskOrNull { from.access() }
			.then { fromContext ->
				MoveTask(
					fromContainers = listOf(from),
					toContainers = listOf(InventoryContainer, HotbarContainer),
					fromSelection = fromStack,
					toSelection = StackSelection.ANYTHING,
					allowReplace = false,
					trackResult = false
				).thenOrNull {
					if (from.count(fromStack) <= 0) {
						fromQueue.removeAll { it.haveMatchingInventories(from) }
					}
					fromContext?.close()
				}
			}
			.then { pushChain(from) }

	/**
	 * Pushes items matching [fromStack] from the player inventory into the first available destination.
	 * If a destination fills up but the player still has items, advances [toQueue] and pushes to the next one.
	 */
	private fun pushChain(from: Container): Task<*> {
		val to = toQueue.firstOrNull { !it.haveMatchingInventories(from) }
			?: return taskOrNull<Unit> { null }

		return taskOrNull { to.access() }
			.then { toContext ->
				MoveTask(
					fromContainers = listOf(InventoryContainer, HotbarContainer),
					toContainers = listOf(to),
					fromSelection = fromStack,
					toSelection = toStack
				).thenOrNull {
					if (!canReceive(to)) {
						toQueue.removeAll { it.haveMatchingInventories(to) }
					}
					toContext?.close()
				}
			}
			.thenOrNull {
				if (isComplete) return@thenOrNull null

				val playerHasItems =
					fromStack.mutate(1) isIn findContainers(ContainerSelection.HOTBAR_AND_INVENTORY).asIterable()
				if (!playerHasItems) return@thenOrNull null

				if (toQueue.any { !it.haveMatchingInventories(from) }) pushChain(from)
				else null
			}
	}

	/**
	 * Moves items between already-accessed containers. Slot pairs are selected fresh each tick using
	 * [fromSelection] and the container's own [Container.findMoveSlots] / [Container.findReplaceSlot] APIs —
	 * no slot references are cached across ticks.
	 *
	 * For partial-stack precision, moves are planned with [StackMovePlanner] which finds the shortest
	 * sequence of vanilla `PICKUP` clicks to reach the exact item count. Whole-stack moves use the
	 * container's own [Container.swap] method which picks the optimal click strategy (e.g. hotbar swap).
	 *
	 * @param allowReplace whether a foreign stack matching [toSelection] may be swapped out as a last resort.
	 * @param trackResult whether destination slots are recorded for the [TransferResult].
	 */
	private inner class MoveTask @Ta5kBuilder constructor(
		private val fromContainers: List<Container>,
		private val toContainers: List<Container>,
		private val fromSelection: StackSelection,
		toSelection: StackSelection,
		private val allowReplace: Boolean = true,
		private val trackResult: Boolean = true
	) : Task<Int>() {
		override val name get() = "Moving ${this@MoveTask.fromSelection}"
		private var moved = 0
		private var lastFailureReason = InvRequestFailureReason.None

		private val toSelection = toSelection.mutate { withoutSelection(this@MoveTask.fromSelection) }

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated { tick() }
			}
		}

		private fun AutomatedSafeContext.tick() {
			lastFailureReason = InvRequestFailureReason.None

			val currentLimit =
				if (remaining == Int.MAX_VALUE) Int.MAX_VALUE
				else (remaining - moved).coerceAtLeast(0)
			if (currentLimit <= 0) {
				finish()
				return
			}

			val currentSelection =
				fromSelection.mutate(
					if (remaining == Int.MAX_VALUE) 0
					else minOf(currentLimit, fromSelection.count.coerceAtLeast(1))
				) { notEmpty() }

			// Find the best slot pair fresh — never cached across ticks
			val moveSlots =
				fromContainers.firstNotNullOfOrNull { fromContainer ->
					val fromSlot = fromContainer.findSlot(currentSelection) ?: return@firstNotNullOfOrNull null

					toContainers.firstNotNullOfOrNull { toContainer ->
						if (toContainer.haveMatchingInventories(fromContainer)) return@firstNotNullOfOrNull null
						if (!fromContainer.canSwapWith(toContainer)) return@firstNotNullOfOrNull null

						// 1. Prefer merging into an existing partial stack of the same item
						val mergeSlot =
							toContainer.findSlot(
								currentSelection.mutate {
									notEmpty()
									predicate { s, _ -> s.hasSpace }
									canInsert(fromSlot.stack)
								}
							)
						if (mergeSlot != null) {
							return@firstNotNullOfOrNull Triple(fromContainer, toContainer, fromSlot to mergeSlot)
						}

						// 2. Otherwise find an empty slot or (if replacement allowed) a foreign replace slot
						val replaceSlot = toContainer.findReplaceSlot(fromSlot.stack, toSelection)
						if (replaceSlot != null && (replaceSlot.stack.isEmpty || (allowReplace && fromSlot.canInsert(replaceSlot.stack)))) {
							Triple(fromContainer, toContainer, fromSlot to replaceSlot)
						} else null
					}
				}

			if (moveSlots == null) {
				finish()
				return
			}

			val (fromContainer, toContainer, pair) = moveSlots
			val (fromSlot, toSlot) = pair

			move(fromContainer, fromSlot, toContainer, toSlot, currentLimit)
		}

		/**
		 * Moves items from [fromSlot] to [toSlot]. Uses native quick-move (shift-click) for whole-stack
		 * moves into matching partial stacks or empty slots. Uses atomic [Container.swap] when replacing
		 * a foreign slot (when [allowReplace] is true). Uses [StackMovePlanner] for partial-stack moves to
		 * reach the exact requested count.
		 */
		private fun AutomatedSafeContext.move(
			fromContainer: Container,
			fromSlot: Slot,
			toContainer: Container,
			toSlot: Slot,
			currentLimit: Int
		) {
			val stack = fromSlot.stack
			if (stack.isEmpty) {
				finish()
				return
			}

			val moveCount = minOf(stack.count, currentLimit)
			if (moveCount <= 0) {
				finish()
				return
			}

			val wholeStack = moveCount == stack.count
			val toSameItem = !toSlot.stack.isEmpty && toSlot.stack.sameItemAs(stack)
			val toForeign = !toSlot.stack.isEmpty && !toSameItem

			// 1. Foreign slot replacement (swapping the whole stack is the sole way into a foreign slot)
			if (toForeign && allowReplace) {
				val actualMoved = stack.count
				val request = fromContainer
					.swapRequest(fromSlot, toSlot, toContainer)
					?.submit()
				if (request == null || !request.done) {
					if (request != null) checkFinish(request.failureReason)
					else finish()
					return
				}

				moved += actualMoved
				if (trackResult) {
					transferred += actualMoved
					resultSlots[toSlot] = toSlot.stack.copy()
					resultContainers.add(toContainer)
				}
				return
			}

			// Can't move into a foreign slot when replacement is not allowed
			if (toForeign) {
				finish()
				return
			}

			// 2. Whole-stack fast-path using native quick-move (shift-click)
			if (wholeStack) {
				val cursorStack = CursorContainer.stacks.firstOrNull() ?: return
				if (!cursorStack.isEmpty) return

				val packetLimit = availablePackets(PacketType.Inventory)
				if (packetLimit < 1) {
					lastFailureReason = InvRequestFailureReason.PacketLimit
					return
				}

				val event = ContainerEvent.Transfer(fromSlot, toSlot, fromContainer, toContainer)
				if (event.post().isCanceled()) return

				val initialStack = stack.copy()
				val countBefore = initialStack.count
				val toSlotsBefore =
					if (trackResult) toContainer.slots.map { it.stack.copy() }
					else null

				val request = inventoryRequest {
					quickMove(fromSlot.id)
				}.submit()

				lastFailureReason = request.failureReason
				if (!request.done) {
					checkFinish(request.failureReason)
					return
				}

				val countAfter =
					if (fromSlot.stack.isEmpty || !fromSlot.stack.sameItemAs(initialStack)) 0
					else fromSlot.stack.count
				val actualMoved = countBefore - countAfter
				if (actualMoved <= 0) {
					finish()
					return
				}

				moved += actualMoved
				if (trackResult) {
					transferred += actualMoved
					toContainer.slots.forEachIndexed { index, slot ->
						val prev = toSlotsBefore?.getOrNull(index)
						if (prev == null || prev.isEmpty || slot.stack.count > prev.count || !slot.stack.sameItemAs(prev)) {
							if (!slot.stack.isEmpty) {
								resultSlots[slot] = slot.stack.copy()
								resultContainers.add(toContainer)
							}
						}
					}
				}
				return
			}

			// Partial move — plan clicks within the currently available packet budget
			if (!CursorContainer.isAccessible) {
				finish()
				return
			}
			val cursorStack = CursorContainer.stacks.firstOrNull() ?: return
			if (!cursorStack.isEmpty && !cursorStack.sameItemAs(stack)) return

			val packetLimit = availablePackets(PacketType.Inventory)
			if (packetLimit < 2) {
				lastFailureReason = InvRequestFailureReason.PacketLimit
				return
			}

			val toCount =
				if (toSameItem) toSlot.stack.count
				else 0
			val maxCount = toSlot.getMaxItemCount(stack)
			val goal = minOf(toCount + moveCount, maxCount)
			if (goal <= toCount) return

			val state = StackMovePlanner.State(cursorStack.count, stack.count, toCount)
			val idealPlan = StackMovePlanner.plan(state, maxCount, goal)
			val (stepGoal, plan) =
				if (idealPlan != null && idealPlan.size <= packetLimit) {
					goal to idealPlan
				} else {
					var foundGoal = goal
					var foundPlan: List<StackMovePlanner.Click>? = null
					for (g in goal downTo toCount + 1) {
						val p = StackMovePlanner.plan(state, maxCount, g) ?: continue
						if (p.size <= packetLimit) {
							foundGoal = g
							foundPlan = p
							break
						}
					}
					foundGoal to foundPlan
				}

			if (plan == null || plan.isEmpty()) {
				lastFailureReason = InvRequestFailureReason.PacketLimit
				return
			}

			val actualMove = stepGoal - toCount
			if (actualMove <= 0) return

			// Post the transfer event for partial moves (swap() posts its own for whole moves)
			val event = ContainerEvent.Transfer(fromSlot, toSlot, fromContainer, toContainer)
			if (event.post().isCanceled()) return

			val request = inventoryRequest {
				plan.forEach { click ->
					val slotId =
						if (click.target == StackMovePlanner.Target.Source) fromSlot.id
						else toSlot.id
					pickup(slotId, click.button)
				}
			}.submit()

			lastFailureReason = request.failureReason
			if (!request.done) return

			moved += actualMove
			if (trackResult) {
				transferred += actualMove
				resultSlots[toSlot] = toSlot.stack.copy()
				resultContainers.add(toContainer)
			}
		}

		private fun checkFinish(failureReason: InvRequestFailureReason = InvRequestFailureReason.None) {
			if (failureReason == InvRequestFailureReason.None) finish()
		}

		private fun finish() {
			success(moved)
		}

		fun ItemStack.sameItemAs(other: ItemStack) = ItemStack.areItemsAndComponentsEqual(this, other)
	}
}
