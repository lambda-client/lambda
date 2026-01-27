/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.player

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.util.Timer
import net.minecraft.entity.EntityType
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.Registries.ITEM
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import kotlin.time.Duration.Companion.milliseconds

object EasyTrash : Module(
    name = "EasyTrash",
    description = "Automatically trashes unwanted items",
    tag = ModuleTag.PLAYER
) {
    private val itemsCanTrash by setting("Items Can Trash", setOf<Item>(Items.NETHERRACK, Items.COBBLESTONE), ITEM.toSet()).onValueChange { _, _ -> setTask() }

    private val dropToPickup by setting("Drop To Pickup", true).onValueChange { _, _ -> setTask() }
    private val itemsToPickup by setting("Items To Pickup", setOf(), ITEM.toSet()).onValueChange { _, _ -> setTask() }

	private var task: EnsureItemPickup? = null

	private val timer = Timer()

	private fun setTask() {
		task?.cancel()
		task = if (dropToPickup) {
			EnsureItemPickup(itemsToPickup, itemsCanTrash, this@EasyTrash).run()
		} else {
			null
		}
	}

    init {
	    onEnable { setTask() }
	    onDisable { task?.cancel(); task = null }

	    listen<TickEvent.Pre> {
			if (task?.isCompleted == true && timer.timePassed(200.milliseconds)) { // prevent rapid re-creation of task
				setTask()
				timer.reset()
			}
	    }

	    listen<InventoryEvent.SlotAction.Click> { event ->
		    if (event.actionType != SlotActionType.QUICK_MOVE || event.button != 0) {
			    return@listen
		    }

		    if (event.screenHandler is GenericContainerScreenHandler) {
				val sh = event.screenHandler
			    val rows = sh.rows

			    if (event.slotId >= rows * 9) {
					// Clicked in inventory, not in container
					return@listen
			    }

			    if (sh.slots.subList(rows * 9, sh.slots.size).any { !it.hasStack() }) {
					// There is empty space in the inventory, no need to trash
					return@listen
			    }

			    StackSelection.selectStack {
				    isOneOfItems(itemsCanTrash)
			    }.filterSlots(InventoryContainer.slots).firstOrNull()?.let { trashSlot ->
				    inventoryRequest {
					    pickup(event.slotId, 0)
					    pickup(trashSlot.id, 0)
					    pickup(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0)
				    }.submit(true)
				    event.cancel()
			    }
		    }
	    }
    }

	class EnsureItemPickup(
		val itemsToPickup: MutableCollection<Item>,
		val trashItems: MutableCollection<Item>,
		automated: Automated
	) : Task<Boolean>(), Automated by automated {
		override val name: String
			get() = "EasyTrash Drop On Entity Task"

		init {
			listen<TickEvent.Pre> {
				if (!tick()) {
					failure("No trashable items to drop")
				}
			}
		}

		fun SafeContext.tick(): Boolean {
			if (player.health > 0.0f && !player.isSpectator && !player.isCreative) {
				val vehicle = player.vehicle
				val box = if (vehicle != null && !vehicle.isRemoved) {
					player.boundingBox.union(vehicle.boundingBox).expand(1.0, 0.0, 1.0)
				} else {
					player.boundingBox.expand(1.0, 0.5, 1.0)
				}
				val onGroundAndInRange = world.getEntitiesByType<ItemEntity>(EntityType.ITEM, box) {
						entity -> itemsToPickup.contains(entity.stack.item) && entity.isOnGround
				}.map { i -> i.stack.item }
				if (onGroundAndInRange.isNotEmpty() && InventoryContainer.spaceAvailable(StackSelection.selectStack { isOneOfItems(onGroundAndInRange) }) <= 0) {
					return dropOneTrashStack()
				}
			}
			return true
		}

		fun SafeContext.dropOneTrashStack(): Boolean {
			StackSelection.selectStack {
				isOneOfItems(trashItems)
			}.filterSlots(InventoryContainer.slots).firstOrNull()?.let {
				inventoryRequest {
					throwStack(it.id)
				}.submit(true)
				return true
			}
			return false
		}
	}
}