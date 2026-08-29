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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainer
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.OpenedContainerContext
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.then
import com.lambda.threading.runSafeAutomated
import net.minecraft.screen.slot.Slot

@Ta5kBuilder
context(automated: Automated)
fun transfer(
	stackSelection: StackSelection,
	fromContainer: Container,
	toContainer: Container
) = ContainerTransferTask(fromContainer, toContainer, stackSelection, automated)

@Ta5kBuilder
@JvmName("transferByStackSelection")
context(automated: Automated)
fun transfer(
	stackSelection: StackSelection,
	toContainer: Container
) = transfer(stackSelection, stackSelection.findContainer() ?: HotbarAndInventoryContainer, toContainer)

@Ta5kBuilder
@JvmName("transferExt")
context(automated: Automated)
fun StackSelection.transferTo(
	toContainer: Container
) = transfer(this, findContainer() ?: HotbarAndInventoryContainer, toContainer)

class ContainerTransferTask @Ta5kBuilder internal constructor(
	private var fromContainer: Container,
	private val toContainer: Container,
	private val selection: StackSelection,
	automated: Automated
) : Task<Slot>(), Automated by automated {
	override val name = "Transferring $selection from $fromContainer to $toContainer"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				when {
					fromContainer.isAccessed && toContainer.isAccessed ->
						TransferTask()
							.onSuccess { success(it) }

					fromContainer !is ExternalContainer ->
						openTransferClose(toContainer, fromContainer, toContainer)

					toContainer !is ExternalContainer ->
						openTransferClose(fromContainer, fromContainer, toContainer)

					else ->
						fromContainer
							.access()
							.then { fromCtx ->
								transfer(selection, fromContainer, HotbarAndInventoryContainer)
									.then { fromCtx.close() }
							}
							.then { toContainer.access() }
							.then { toCtx -> transferAndClose(toCtx, HotbarAndInventoryContainer, toContainer) }
				}.execute(this@ContainerTransferTask)
			}
		}
	}

	/**
	 * Opens [containerToOpen], transfers [selection] from [from] to [to], then closes the opened container.
	 *
	 * Used when exactly one of the two containers is external and needs to be accessed.
	 */
	private fun openTransferClose(containerToOpen: Container, from: Container, to: Container) =
		containerToOpen
			.access()
			.then { ctx -> transferAndClose(ctx, from, to) }

	/**
	 * Transfers [selection] from [from] to [to], then closes the container via [ctx].
	 */
	private fun transferAndClose(ctx: OpenedContainerContext, from: Container, to: Container) =
		transfer(selection, from, to)
			.then { slot ->
				ctx
					.close()
					.onSuccess { success(slot) }
			}

	private inner class TransferTask @Ta5kBuilder constructor() : Task<Slot>() {
		override val name = "Transferring"

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					val (fromSlot, toSlot) = fromContainer.getTransferSlots(selection, toContainer)
					if (fromSlot == null) {
						checkFail()
						return@listen
					}
					if (toSlot == null) {
						failure("Unable to find a slot to transfer to.")
						return@listen
					}
					val transferSuccessful = fromContainer.swap(fromSlot, toSlot, toContainer)
					if (transferSuccessful) success(toSlot)
					else checkFail()
				}
			}
		}

		private fun checkFail() {
			failure(NoMaterialAccessException(selection))
		}

		private inner class NoMaterialAccessException(stackSelection: StackSelection) : IllegalStateException("Unable to access $stackSelection.")
	}
}