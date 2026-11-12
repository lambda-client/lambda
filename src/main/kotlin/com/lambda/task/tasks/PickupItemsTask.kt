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

	private var target: ItemEntity? = null
	private var travel: Task<*>? = null
	private var ticksInReach = 0
	private var walks = 0

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
			stopTravelIfGone()
		}

		listen<InventoryEvent.PickupMerged>(alwaysListen = true) { event ->
			if (drops.remove(event.source)) drops += event.target
			stopTravelIfGone()
		}

		listen<InventoryEvent.PickupLost>(alwaysListen = true) { event ->
			if (event.remaining <= 0) drops.remove(event.source)
			stopTravelIfGone()
		}
	}

	private fun stopTravelIfGone() {
		if (target == null || target in drops) return
		target = null
		travel?.cancel()
		travel = null
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

		if (target !== this@PickupItemsTask.target) {
			this@PickupItemsTask.target = target
			ticksInReach = 0
			walks = 0
		}

		val inReach = player.blockPos.getSquaredDistance(target.blockPos) <= PICKUP_RANGE * PICKUP_RANGE
		if (inReach && ticksInReach++ < PICKUP_WAIT_TICKS) return
		if (walks++ >= MAX_WALKS) {
			drops.remove(target)
			return
		}

		travel = travelTo(target.blockPos, if (inReach) 0 else PICKUP_RANGE)
			.softFail()
			.start()
	}

	private companion object {
		const val PICKUP_RANGE = 1
		const val PICKUP_WAIT_TICKS = 10
		const val MAX_WALKS = 4
	}
}
