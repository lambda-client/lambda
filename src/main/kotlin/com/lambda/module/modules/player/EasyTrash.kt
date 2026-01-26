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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.InventoryContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
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
    "EasyTrash",
    "Automatically trashes unwanted items",
    ModuleTag.PLAYER
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
    }

    fun SafeContext.checkShouldTrashSomething() {
        if (player.health > 0.0f && !player.isSpectator && dropToPickup && !player.isCreative) {
            val box = if (player.vehicle != null && !player.vehicle!!.isRemoved) {
                player.boundingBox.union(player.vehicle!!.boundingBox).expand(1.0, 0.0, 1.0)
            } else {
                player.boundingBox.expand(1.0, 0.5, 1.0)
            }
            val items = world.getEntitiesByType<ItemEntity>(EntityType.ITEM, box) { entity -> itemsToPickup.contains(entity.stack.item) && entity.isOnGround }.map { i -> i.stack.item }
            if (items.isNotEmpty() && InventoryContainer.spaceAvailable(StackSelection.selectStack { isOneOfItems(items) }) == 0) {
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

    /**
     * Called when a slot is clicked in a screen handler.
     * Returns true if the click was handled
     */
    @JvmStatic
    fun onClick(slotIndex: Int, button: Int, actionType: SlotActionType): Boolean =
        runSafe {
            if (!isEnabled) return false
            if (actionType != SlotActionType.QUICK_MOVE || button != 0) return false
            val screenHandler = player.currentScreenHandler

            if (screenHandler is GenericContainerScreenHandler) {
                val slot = screenHandler.getSlot(slotIndex)
                val rows = screenHandler.rows

                if (slotIndex >= rows * 9) return false // Not in the container

                val inventorySlots = screenHandler.slots.subList(rows * 9, screenHandler.slots.size)
                val freeInventorySlot = inventorySlots.any { !it.hasStack() }
                if (freeInventorySlot) return false

                StackSelection.selectStack {
                    isOneOfItems(itemsCanTrash)
                }.filterSlots(InventoryContainer.slots).firstOrNull()?.let { trashSlot ->
                    inventoryRequest {
                        pickup(slot.id, 0)
                        pickup(trashSlot.id, 0)
                        pickup(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0)
                    }.submit(true)
                    return true
                }
            }
            return false
        } ?: false
}