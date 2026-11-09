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
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.findSlot
import com.lambda.interaction.handler.handlers.ContainerHandler.spaceLeft
import com.lambda.interaction.manager.managers.inventory.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.TaskMarker
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.threading.withSafeAutomated
import com.lambda.util.item.ItemStackUtils.itemAndComponentsEqual
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack

@TaskMarker
context(automated: Automated)
fun pickupItems(
	drops: MutableSet<ItemEntity>,
	dropDisposablesToMakeSpace: Boolean = true
) = PickupItemsTask(drops, dropDisposablesToMakeSpace, automated)

data class PickedUpItem(
	val stack: ItemStack,
	val slot: SlotIdentity
)

context(_: Automated)
fun SafeContext.canPickup(stack: ItemStack): Boolean {
	val offhand = player.offHandStack
	return (offhand.itemAndComponentsEqual(stack) && offhand.count < offhand.maxCount) ||
			spaceLeft(stack.item.select(1), ContainerSelection.HOTBAR_AND_INVENTORY) >= 1
}

class PickupItemsTask @TaskMarker internal constructor(
	private val drops: MutableSet<ItemEntity>,
	private val dropDisposablesToMakeSpace: Boolean = true,
	automated: Automated
) : Task<List<PickedUpItem>>(), Automated by automated {
	override val name get() = "Collecting ${drops.size} item drop(s)"

	private val pickups = mutableListOf<PickedUpItem>()

	init {
		listen<TickEvent.Pre> {
			withSafeAutomated {
				pollNextItem()
			}
		}

		listen<InventoryEvent.Pickup>(alwaysListen = true) { event ->
			if (event.source !in drops) return@listen
			event.slots.forEach { (slot, landed) ->
				pickups += PickedUpItem(landed.stack.copyWithCount(landed.amount), slot)
			}
			if (event.remaining <= 0) drops.remove(event.source)
		}

		listen<InventoryEvent.PickupMerged>(alwaysListen = true) { event ->
			if (drops.remove(event.source)) drops += event.target
		}

		listen<InventoryEvent.PickupLost>(alwaysListen = true) { event ->
			if (event.remaining <= 0) drops.remove(event.source)
		}
	}

	private fun AutomatedSafeContext.pollNextItem() {
		if (drops.isEmpty()) {
			success(pickups.toList())
			return
		}

		val live = drops.filter { !it.isRemoved }
		if (live.isEmpty()) return

		val target = live.firstOrNull { canPickup(it.stack) }
		if (target == null) {
			if (!dropDisposablesToMakeSpace) {
				success(pickups.toList())
				return
			}
			val disposable =
				findSlot(
					stackSelection { ofAnyItems(inventoryConfig.disposables) },
					ContainerSelection.HOTBAR_AND_INVENTORY,
					sorted = false
				) ?: run {
					success(pickups.toList())
					return
				}

			inventoryRequest {
				throwStack(disposable.id)
			}.submit()
			return
		}

		travelTo(target.blockPos)
			.softFail()
			.start()
	}
}
