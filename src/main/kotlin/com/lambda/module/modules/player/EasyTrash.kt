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

import com.lambda.context.SafeContext
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
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
    private val itemsCanTrash by setting("Items Can Trash", setOf<Item>(Items.NETHERRACK, Items.COBBLESTONE), ITEM.toSet())

    private val dropToPickup by setting("Drop To Pickup", true)
    private val itemsToPickup by setting("Items To Pickup", setOf(), ITEM.toSet())

    private val timer = Timer()

    init {
        listen<TickEvent.Pre> {
            if (!timer.timePassed(200.milliseconds)) {
                return@listen
            }
            checkShouldTrashSomething()
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

    fun SafeContext.checkShouldTrashSomething() {
        if (player.health > 0.0f && !player.isSpectator && dropToPickup && !player.isCreative) {
			val vehicle = player.vehicle
            val box = if (vehicle != null && !vehicle.isRemoved) {
                player.boundingBox.union(vehicle.boundingBox).expand(1.0, 0.0, 1.0)
            } else {
                player.boundingBox.expand(1.0, 0.5, 1.0)
            }
            val items = world.getEntitiesByType<ItemEntity>(EntityType.ITEM, box) {
				entity -> itemsToPickup.contains(entity.stack.item) && entity.isOnGround
			}.map { i -> i.stack.item }
            if (items.isNotEmpty() && InventoryContainer.spaceAvailable(StackSelection.selectStack { isOneOfItems(items) }) <= 0) {
                if (trashSomething()) timer.reset()
            }
        }
    }

    fun SafeContext.trashSomething(): Boolean {
        StackSelection.selectStack {
            isOneOfItems(itemsCanTrash)
        }.filterSlots(InventoryContainer.slots).firstOrNull()?.let {
            inventoryRequest {
                throwStack(it.id)
            }.submit(true)
            return true
        }
        return false
    }
}