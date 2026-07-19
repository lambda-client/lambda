
package com.minato.task.tasks

import com.minato.context.Automated
import com.minato.event.EventFlow.post
import com.minato.event.events.ContainerEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.ExternalContainer
import com.minato.interaction.material.container.MaterialContainer
import com.minato.interaction.material.container.containers.InventoryContainer
import com.minato.task.Task
import com.minato.threading.runSafeAutomated

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


	private fun checkFail(): Boolean =
		failIfNoMaterial.also {
			failure(NoMaterialAccessException(stackSelection))
		}

	private class NoMaterialAccessException(stackSelection: StackSelection) : IllegalStateException("Unable to access $stackSelection.")
}