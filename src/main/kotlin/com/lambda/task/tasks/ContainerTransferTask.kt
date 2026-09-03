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
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.handler.handlers.findContainer
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
	fromSelection: ContainerSelection = ContainerSelection.EVERYTHING,
	toSelection: ContainerSelection
) = ContainerTransferTask(selection, fromSelection, toSelection, automated)

@Ta5kBuilder
@JvmName("transferExt")
context(automated: Automated)
fun StackSelection.transferTo(
	fromSelection: ContainerSelection = ContainerSelection.EVERYTHING,
	toSelection: ContainerSelection
) = transfer(this, fromSelection, toSelection)

class ContainerTransferTask @Ta5kBuilder internal constructor(
	private val selection: StackSelection,
	private val fromSelection: ContainerSelection,
	private val toSelection: ContainerSelection,
	automated: Automated
) : Task<Slot>(), Automated by automated {
	override val name = "Transferring $selection from $fromSelection to $toSelection"

	override fun SafeContext.onStart() {
		val fromContainer = findContainer(containerSelection)
			?: run {
				failure(IllegalStateException("Could not find container to pull $selection from"))
				return
			}
		when {
			fromContainer.isAccessed && toContainer.isAccessed ->
				TransferTask()
					.onSuccess { success(it) }

			fromContainer !is ExternalContainer ->
				openTransferClose(toContainer, toContainer, containerSelection)

			toContainer !is ExternalContainer ->
				openTransferClose(fromContainer, toContainer, containerSelection)

			else -> {
				taskOrNull { fromContainer.access() }
					.then { fromCtx ->
						transfer(selection, toContainer, ContainerSelection.HOTBAR_AND_INVENTORY)
							.thenOrNull { fromCtx?.close() }
					}
					.thenOrNull { toContainer.access() }
					.then { toCtx -> transferAndClose(toCtx, toContainer, ContainerSelection.HOTBAR_AND_INVENTORY) }
			}
		}?.start()
	}

	/**
	 * Opens [containerToOpen], transfers [selection] from [from] to [to], then closes the opened container.
	 *
	 * Used when exactly one of the two containers is external and needs to be accessed.
	 */
	@Ta5kBuilder
	private fun openTransferClose(
		containerToOpen: Container,
		to: Container,
		containerSelection: ContainerSelection
	) = containerToOpen
		.access()
		?.then { ctx -> transferAndClose(ctx, to, containerSelection) }

	/**
	 * Transfers [selection] from [from] to [to], then closes the container via [ctx].
	 */
	@Ta5kBuilder
	private fun transferAndClose(
		ctx: OpenedContainerContext?,
		fromSelection: ContainerSelection,
		toSelection: ContainerSelection
	) = transfer(selection, fromSelection, toSelection)
		.thenOrNull { slot ->
			ctx
				?.close()
				?.onSuccess { success(slot) }
		}

	private inner class TransferTask @Ta5kBuilder constructor() : Task<Slot>() {
		override val name = "Transferring"

		private var transferred = 0
		private var lastSlot: Slot? = null

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					while (selection.count == 0 || transferred < selection.count) {
						val (fromSlot, toSlot) = fromContainer.getTransferSlots(selection, toContainer)

						if (fromSlot == null || toSlot == null) {
							lastSlot?.let { slot ->
								if (selection.count == 0 || transferred >= selection.count) {
									success(slot)
									return@listen
								}
							}
							if (fromSlot == null) checkFail()
							else failure("Unable to find a slot to transfer to.")
							return@listen
						}

						val moveCount = fromSlot.stack.count
						if (!fromContainer.swap(fromSlot, toSlot, toContainer)) {
							checkFail()
							return@listen
						}

						transferred += moveCount
						lastSlot = toSlot

						// count == 0 means single stack transfer
						if (selection.count == 0) {
							success(toSlot)
							return@listen
						}
					}

					lastSlot?.let { success(it) } ?: checkFail()
				}
			}
		}

		private fun checkFail() {
			failure(IllegalStateException("Unable to access $selection"))
		}
	}
}