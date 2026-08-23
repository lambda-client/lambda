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
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.task.Task
import com.lambda.task.wrappers.thenAction
import com.lambda.threading.runSafeAutomated
import net.minecraft.screen.slot.Slot

class ContainerTransferTask @Ta5kBuilder constructor(
	private var fromContainer: Container,
	private val toContainer: Container,
	private val stackSelection: StackSelection,
	private val failIfNoStack: Boolean = false,
	automated: Automated
) : Task<Slot>(), Automated by automated {
	override val name = "Transferring $stackSelection from $fromContainer to $toContainer"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				if (fromContainer is ExternalContainer && toContainer is ExternalContainer) {
					fromContainer.accessThen(
						afterOpen = {
							fromContainer.transferByTask(stackSelection, HotbarAndInventoryContainer, failIfNoStack)
						}
					) {
						HotbarAndInventoryContainer.transferByTask(stackSelection, toContainer, failIfNoStack)
							.finally { slot -> success(slot) }
					}?.execute(this@ContainerTransferTask)
					return@listen
				}

				fromContainer.accessThen {
					toContainer.accessThen {
						TransferTask().finally { slot ->
							success(slot)
						}
					}
				}.execute(this@ContainerTransferTask)
			}
		}
	}

	private inner class TransferTask : Task<Slot>() {
		override val name = "Transferring"

		init {
			listen<TickEvent.Pre> {
				runSafeAutomated {
					val (fromSlot, toSlot) = fromContainer.getTransferSlots(stackSelection, toContainer)
					if (fromSlot == null) {
						checkFail()
						return@listen
					}
					if (toSlot == null) {
						failure("Unable to find a slot to transfer to.")
						return@listen
					}
					val transferSuccessful = fromContainer.transfer(fromSlot, toSlot, toContainer)
					if (transferSuccessful) success(toSlot)
					else checkFail()
				}
			}
		}

		private fun checkFail() {
			if (failIfNoStack) failure(NoMaterialAccessException(stackSelection))
		}

		private inner class NoMaterialAccessException(stackSelection: StackSelection) : IllegalStateException("Unable to access $stackSelection.")
	}

	companion object {
		@Ta5kBuilder
		context(automated: Automated)
		fun transfer(
			fromContainer: Container,
			toContainer: Container,
			stackSelection: StackSelection,
			failIfNoStack: Boolean = false
		) = ContainerTransferTask(fromContainer, toContainer, stackSelection, failIfNoStack, automated)

		@Ta5kBuilder
		context(automated: Automated)
		fun transfer(
			stackSelection: StackSelection,
			toContainer: Container,
			failIfNoStack: Boolean = false
		) = stackSelection.findContainer()?.let {
			ContainerTransferTask(it, toContainer, stackSelection, failIfNoStack, automated)
		}

		@Ta5kBuilder
		context(automated: Automated)
		fun StackSelection.transfer(
			toContainer: Container,
			failIfNoStack: Boolean = false
		) = findContainer()?.let {
			ContainerTransferTask(it, toContainer, this, failIfNoStack, automated)
		}
	}
}