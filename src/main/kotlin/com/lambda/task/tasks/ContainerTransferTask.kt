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
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated

class ContainerTransferTask(
	private var fromContainer: MaterialContainer,
	private val toContainer: MaterialContainer,
	private val stackSelection: StackSelection,
	automated: Automated,
	private val failIfNoMaterial: Boolean = false
) : Task<Unit>(), Automated by automated {
	override val name = "Transferring $stackSelection from $fromContainer to $toContainer"

	private var delegateTask: Task<*>? = null

	init {
		listen<TickEvent.Pre> {
			if (delegateTask?.isCompleted == true) {
				success()
				return@listen
			}
			runSafeAutomated {
				val fromExternal = fromContainer as? ExternalContainer
				val slots = fromContainer.slots
				val toSlots = toContainer.slots
				fromExternal.takeIf { slots.isEmpty() }?.let { fromExternal ->
					if (toContainer is ExternalContainer && toSlots.isEmpty()) {
						fromContainer.transferByTask(stackSelection, InventoryContainer).finally {
							fromContainer = InventoryContainer
						}.execute(this@ContainerTransferTask)
						return@listen
					}
					delegateTask = fromExternal.accessThen {
						fromContainer.transferByTask(stackSelection, toContainer)
					}?.execute(this@ContainerTransferTask) ?: run {
						checkFail()
						return@listen
					}
					return@listen
				}
				if (toContainer is ExternalContainer && toSlots.isEmpty()) {
					delegateTask = toContainer.accessThen {
						fromContainer.transferByTask(stackSelection, toContainer)
					}?.execute(this@ContainerTransferTask) ?: run {
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


	private fun checkFail() {
		if (failIfNoMaterial) failure(NoMaterialAccessException(stackSelection))
		else success()
	}

	private class NoMaterialAccessException(stackSelection: StackSelection) : IllegalStateException("Unable to access $stackSelection.")
}