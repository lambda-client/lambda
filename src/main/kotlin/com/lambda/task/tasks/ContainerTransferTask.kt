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
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated

class ContainerTransferTask(
	private val fromContainer: MaterialContainer,
	private val destination: MaterialContainer,
	private val stackSelection: StackSelection,
	automated: Automated,
	private val failIfNoMaterial: Boolean = false
) : Task<Unit>(), Automated by automated {
	override val name = "Transferring $stackSelection from $fromContainer to $destination"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				val slots = fromContainer.slots
				val toSlots = destination.slots
				if (fromContainer is ExternalContainer && slots.isEmpty()) {
					if (destination is ExternalContainer && toSlots.isEmpty()) {
						fromContainer.transferByTask(stackSelection, InventoryContainer).then {
							InventoryContainer.transferByTask(stackSelection, destination).finally { success() }
						}
					}
					fromContainer.access().execute(this@ContainerTransferTask).then {
						fromContainer.transferByTask(stackSelection, destination).finally { success() }
					}
					return@listen
				} else if (destination is ExternalContainer && toSlots.isEmpty()) {
					destination.access().execute(this@ContainerTransferTask).then {
						fromContainer.transferByTask(stackSelection, destination).finally { success() }
					}
					return@listen
				}

				fromContainer.getSlot(stackSelection)?.let { fromSlot ->
					destination.getReplaceSlot()?.let { toSlot ->
						inventoryRequest {
							if (fromContainer.swapMethodPriority > destination.swapMethodPriority)
								with(fromContainer) { transfer(fromSlot, toSlot) }
							else with(destination) { transfer(toSlot, toSlot) }
							onComplete { success() }
						}.submit()
						return@listen
					}
				}

				if (failIfNoMaterial) {
					failure("$stackSelection not found.")
					return@listen
				}
			}
		}
	}
}