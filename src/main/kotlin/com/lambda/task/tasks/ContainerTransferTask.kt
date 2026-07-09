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
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ContainerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.interaction.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated

class ContainerTransferTask(
	private var fromContainer: Container,
	private val toContainer: Container,
	private val stackSelection: StackSelection,
	automated: Automated,
	private val failIfNoMaterial: Boolean = false
) : Task<Unit>(), Automated by automated {
	override val name = "Transferring $stackSelection from $fromContainer to $toContainer"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				val slots = fromContainer.slots
				val toSlots = toContainer.slots
				if (fromContainer is ExternalContainer && slots.isEmpty()) {
					if (toContainer is ExternalContainer && toSlots.isEmpty()) {
						fromContainer
							.transferByTask(stackSelection, HotbarAndInventoryContainer)
							.finally { fromContainer = HotbarAndInventoryContainer }
							.execute(this@ContainerTransferTask)
						return@listen
					}
					return@listen
				}
				if (toContainer is ExternalContainer && toSlots.isEmpty()) {
					toContainer
						.accessThen {
							fromContainer.transferByTask(stackSelection, toContainer)
						}?.finally { success() }
						?.execute(this@ContainerTransferTask) ?: run {
							checkFail()
							return@listen
						}
					return@listen
				}

				fromContainer.getSlot(stackSelection)?.let { fromSlot ->
					toContainer.getReplaceableSlot()?.let { toSlot ->
						val transferEvent = ContainerEvent.Transfer(fromSlot, toSlot, fromContainer, toContainer)
						if (transferEvent.post().isCanceled()) failure("Transfer prevented by an internal interruption")
						inventoryRequest {
							if (fromContainer.swapMethodPriority > toContainer.swapMethodPriority)
								with(fromContainer) { transfer(fromSlot, toSlot) }
							else with(toContainer) { transfer(toSlot, fromSlot) }
							onComplete { success() }
						}.submit()
						return@listen
					}
				}

				checkFail()
			}
		}
	}


	private fun checkFail(): Boolean =
		if (failIfNoMaterial) {
			failure(NoMaterialAccessException(stackSelection))
			true
		} else false

	private class NoMaterialAccessException(stackSelection: StackSelection) : IllegalStateException("Unable to access $stackSelection.")
}