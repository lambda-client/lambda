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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ExternalContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.containerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.mutate
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.stackSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.container.selection.selectContainers
import com.lambda.interaction.handler.handlers.findContainers
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.threading.runSafeAutomated
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

	private var transferred = 0
	private val resultSlots = mutableListOf<Slot>()
	private val resultContainers = mutableSetOf<Container>()

	override fun SafeContext.onStart() {
		val fromContainers =
			findContainers(
				containerSelection(fromSelection.scope) {
					matches(fromSelection)
					hasStack(fromStack.mutate(1))
				}
			).toList()
				.takeIf { fromStack isIn it }
				?: run {
					failure("Could not find source containers for $fromStack")
					return
				}

		val toContainers =
			findContainers(
				containerSelection(toSelection.scope) {
					matches(toSelection)
					hasSpace(fromStack.mutate(1))
				}
			).toList()
				.takeIf { fromStack spaceIn it }
				?: run {
					failure("Could not find destination containers with space for $fromStack")
					return
				}

		transferNextPair(fromContainers.toMutableList(), toContainers.toMutableList())
	}

	private fun remainingSelection() =
		fromStack.mutate(count = if (fromStack.count <= 0) 0 else fromStack.count - transferred)

	private fun isComplete() =
		fromStack.count > 0 && transferred >= fromStack.count
	private fun buildResult() =
		TransferResult(
			stackSelection {
				predicate { _, slot ->
					slot != null &&
							resultSlots.any {
								slot.inventory::class == it.inventory::class && slot.index == it.index
							}
				}
			},
			selectContainers(*resultContainers.toTypedArray())
		)

	/**
	 * Takes the first container from each queue as a pair, builds a transfer
	 * chain for that pair, then advances to the next pair on completion.
	 *
	 * Calls [success] or [failure] directly on this task when done.
	 */
	private fun transferNextPair(
		fromQueue: MutableList<Container>,
		toQueue: MutableList<Container>
	) {
		if (isComplete()) {
			success(buildResult())
			return
		}

		if (fromQueue.isEmpty() || toQueue.isEmpty()) {
			failure("Not enough containers to transfer $fromStack (transferred $transferred/${fromStack.count})")
			return
		}

		val fromContainer = fromQueue.first()
		val toContainer = toQueue.first()

		val bothExternal = fromContainer is ExternalContainer && !fromContainer.isAccessed &&
				toContainer is ExternalContainer && !toContainer.isAccessed

		val pairChain =
			if (bothExternal) pullPushChain(fromContainer, toContainer, toQueue)
			else directPairChain(fromContainer, toContainer)

		pairChain.onSuccess {
			if (fromContainer.count(fromStack) <= 0) fromQueue.remove(fromContainer)
			if (!bothExternal && toContainer.spaceLeft(fromStack) <= 0) toQueue.remove(toContainer)
			transferNextPair(fromQueue, toQueue)
		}.onFailure {
			failure(it)
		}.execute(this@ContainerTransferTask)
	}

	/**
	 * Builds a chain for a pair where at most one container is external.
	 * Uses [taskOrNull] to uniformly handle already-accessed containers.
	 */
	private fun directPairChain(
		fromContainer: Container,
		toContainer: Container
	) =
		taskOrNull { fromContainer.access() }.then { fromContext ->
			taskOrNull { toContainer.access() }.then { toContext ->
				SwapTask(fromContainer.select(), toContainer.select())
					.thenOrNull { toContext?.close() }
					.thenOrNull { fromContext?.close() }
			}
		}

	/**
	 * Builds a chain for when both containers are external.
	 * Pulls items from [fromContainer] into the player inventory, then delegates
	 * to [pushChain] to push them into [toContainer].
	 */
	private fun pullPushChain(
		fromContainer: Container,
		toContainer: Container,
		toQueue: MutableList<Container>
	) =
		taskOrNull { fromContainer.access() }.then { fromContext ->
			SwapTask(
				fromContainer.select(),
				ContainerSelection.HOTBAR_AND_INVENTORY,
				replaceSelection = StackSelection.ANYTHING,
				trackResult = false
			).thenOrNull { fromContext?.close() }
		}.then { pushChain(toContainer, toQueue) }

	/**
	 * Pushes items from the player inventory into [destination].
	 * If [destination] fills up but the player still has items, advances [toQueue]
	 * and pushes to the next destination.
	 */
	private fun pushChain(
		destination: Container,
		toQueue: MutableList<Container>
	): Task<*> {
		val chain = taskOrNull { destination.access() }.then { destinationContext ->
			SwapTask(ContainerSelection.HOTBAR_AND_INVENTORY, destination.select())
				.thenOrNull { destinationContext?.close() }
		}
		chain.onSuccess {
			if (isComplete()) return@onSuccess

			if (destination.spaceLeft(fromStack) == 0) toQueue.remove(destination)

			val playerHasItems = findContainers(ContainerSelection.HOTBAR_AND_INVENTORY)
				.any { container -> fromStack.mutate(1) isIn container }
			if (!playerHasItems) return@onSuccess

			val nextDestination = toQueue.firstOrNull() ?: return@onSuccess
			pushChain(nextDestination, toQueue).execute(this@ContainerTransferTask)
		}.onFailure {
			failure(it)
		}
		return chain
	}

	/**
	 * Swaps items between containers matching [fromSelection] and [toSelection] one slot per tick.
	 * Completes when no more valid move slots can be found or the total count is reached.
	 * Updates [transferred], [resultSlots], and [resultContainers] on the outer task.
	 *
	 * @param trackResult whether to record destination slots in [resultSlots]/[resultContainers].
	 *   Set to `false` for intermediary transfers (e.g. pulling to player inventory).
	 */
	private inner class SwapTask @Ta5kBuilder constructor(
		private val fromSelection: ContainerSelection,
		private val toSelection: ContainerSelection,
		private val replaceSelection: StackSelection = toStack,
		private val trackResult: Boolean = true
	) : Task<Unit>() {
		override val name = "Swapping slots"

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					swapStack()
				}
			}
		}

		private fun AutomatedSafeContext.swapStack(): Boolean {
			val currentSelection = remainingSelection()

			val moveSlots =
				findContainers(fromSelection).firstNotNullOfOrNull { fromContainer ->
					findContainers(toSelection).firstNotNullOfOrNull { toContainer ->
						val (from, to) =
							fromContainer.findMoveSlots(
								currentSelection,
								toContainer,
								replaceSelection
							)
						if (from != null && to != null) {
							Triple(
								fromContainer,
								toContainer,
								Pair(from, to)
							)
						} else null
					}
				}

			if (moveSlots == null) {
				success()
				return true
			}

			val (fromContainer, toContainer, slots) = moveSlots
			val (fromSlot, toSlot) = slots
			val moveCount =
				if (fromStack.count <= 0) fromSlot.stack.count
				else minOf(fromSlot.stack.count, fromStack.count - transferred)

			if (!fromContainer.swap(fromSlot, toSlot, toContainer)) {
				failure("Swap failed for $fromStack")
				return true
			}

			transferred += moveCount
			if (trackResult) {
				resultSlots.add(toSlot)
				resultContainers.add(toContainer)
			}

			if (isComplete()) {
				success()
				return true
			}

			return false
		}
	}
}