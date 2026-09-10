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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ExternalContainer
import com.lambda.interaction.container.OpenedContainerContext
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.mutate
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.mutate
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
	selection: StackSelection,
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.ANYTHING
) = ContainerTransferTask(selection, fromSelection, toSelection, toStack, automated)

@Ta5kBuilder
@JvmName("transferExt")
context(automated: Automated)
fun StackSelection.transfer(
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection,
	toStack: StackSelection = StackSelection.ANYTHING
) = transfer(this, fromSelection, toSelection, toStack)

class ContainerTransferTask @Ta5kBuilder internal constructor(
	private val fromStack: StackSelection,
	private val fromSelection: ContainerSelection,
	private val toSelection: ContainerSelection,
	private val toStack: StackSelection,
	automated: Automated
) : Task<Slot>(), Automated by automated {
	override val name = "Transferring $fromStack from $fromSelection to $toSelection"

	override fun SafeContext.onStart() {
		val singleSelection = fromStack.mutate(count = if (fromStack.count == 0) 0 else 1)

		val fromContainers =
			findContainers(
				fromSelection.mutate {
					hasStack(fromStack)
				}
			).toList()
		val toContainers =
			findContainers(
				toSelection.mutate {
					hasStack(toStack)
				}
			).toList()

		if (fromContainers.isEmpty()) {
			failure(IllegalStateException("Could not find any container to pull $fromStack from"))
			return
		}
		if (toContainers.isEmpty()) {
			failure(IllegalStateException("Could not find any container with space to push $fromStack to"))
			return
		}

		if (fromStack.count > 0) {
			val totalAvailable = fromContainers.sumOf { it.count(singleSelection) }
			if (totalAvailable < fromStack.count) {
				failure(IllegalStateException("Not enough items across all containers to pull $fromStack"))
				return
			}
		}

		val fromContainer = fromContainers.first()
		val toContainer = toContainers.first()

		when {
			fromContainers.all { it.isAccessed } && toContainers.all { it.isAccessed } ->
				TransferTask(fromContainers, toContainers)
					.onSuccess { success(it) }

			fromContainer !is ExternalContainer ->
				openTransferClose(toContainer, fromSelection, toSelection)

			toContainer !is ExternalContainer ->
				openTransferClose(fromContainer, fromSelection, toSelection)

			else ->
				taskOrNull { fromContainer.access() }
					.then { fromCtx ->
						transfer(fromStack, fromSelection, ContainerSelection.HOTBAR_AND_INVENTORY)
							.thenOrNull { fromCtx?.close() }
					}
					.thenOrNull { toContainer.access() }
					.then { toCtx -> transferAndClose(toCtx, ContainerSelection.HOTBAR_AND_INVENTORY, toSelection) }
		}?.start()
	}

	/**
	 * Opens [containerToOpen], transfers [fromStack] from [from] to [to], then closes the opened container.
	 *
	 * Used when exactly one of the two containers is external and needs to be accessed.
	 */
	@Ta5kBuilder
	private fun openTransferClose(
		containerToOpen: Container,
		fromSelection: ContainerSelection,
		toSelection: ContainerSelection
	) = containerToOpen
		.access()
		?.then { ctx -> transferAndClose(ctx, fromSelection, toSelection) }

	/**
	 * Transfers [fromStack] from [from] to [to], then closes the container via [ctx].
	 */
	@Ta5kBuilder
	private fun transferAndClose(
		ctx: OpenedContainerContext?,
		fromSelection: ContainerSelection,
		toSelection: ContainerSelection
	) = transfer(fromStack, fromSelection, toSelection)
		.thenOrNull { slot ->
			ctx
				?.close()
				?.onSuccess { success(slot) }
		}

	private inner class TransferTask @Ta5kBuilder constructor(
		private val fromContainers: List<Container>,
		private val toContainers: List<Container>
	) : Task<Slot>() {
		override val name = "Transferring"

		private var transferred = 0
		private var lastSlot: Slot? = null

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					while (fromStack.count == 0 || transferred < fromStack.count) {
						val currentSelection = fromStack.mutate(count = if (fromStack.count == 0) 0 else fromStack.count - transferred)

						var fromSlot: Slot? = null
						var toSlot: Slot? = null
						var fromContainer: Container? = null
						var toContainer: Container? = null

						for (fc in fromContainers) {
							for (tc in toContainers) {
								val slots = fc.findMoveSlots(currentSelection, tc, toStack)
								if (slots.first != null && slots.second != null) {
									fromSlot = slots.first
									toSlot = slots.second
									fromContainer = fc
									toContainer = tc
									break
								}
							}
							if (fromSlot != null) break
						}

						if (fromSlot == null || toSlot == null || fromContainer == null || toContainer == null) {
							lastSlot?.let { slot ->
								if (fromStack.count == 0 || transferred >= fromStack.count) {
									success(slot)
									return@listen
								}
							}
							checkFail()
							return@listen
						}

						val moveCount = minOf(fromSlot.stack.count, if (fromStack.count == 0) Int.MAX_VALUE else fromStack.count - transferred)
						if (!fromContainer.swap(fromSlot, toSlot, toContainer)) {
							checkFail()
							return@listen
						}

						transferred += moveCount
						lastSlot = toSlot

						// count == 0 means single stack transfer
						if (fromStack.count == 0) {
							success(toSlot)
							return@listen
						}
					}

					lastSlot?.let { success(it) } ?: checkFail()
				}
			}
		}

		private fun checkFail() {
			failure(IllegalStateException("Unable to access $fromStack"))
		}
	}
}