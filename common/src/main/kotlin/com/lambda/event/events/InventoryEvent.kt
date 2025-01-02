/*
 * Copyright 2024 Lambda
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

package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler

/**
 * Represents various events related to inventory interactions, updates, and state changes.
 *
 * This sealed class encompasses different types of inventory events that occur during gameplay,
 * such as opening or closing an inventory, updating inventory contents, or changing hotbar slots.
 * Each event provides specific contextual data relevant to the inventory interaction.
 */
sealed class InventoryEvent {
    /**
     * Represents an event triggered when an inventory or screen is opened.
     *
     * This event provides access to the associated [ScreenHandler], which manages the interaction
     * logic between the inventory and the player. It is typically used to initialize or handle
     * logic when the screen linked to a player's inventory is opened.
     *
     * @property screenHandler The screen handler associated with the opened inventory.
     */
    data class Open(val screenHandler: ScreenHandler) : Event

    /**
     * Represents an event triggered when an inventory or screen is closed.
     *
     * This event provides access to the associated [ScreenHandler], which manages the interaction
     * logic between the inventory and the player. It is typically used to finalize or clean up logic
     * when a player's inventory screen is closed.
     *
     * @property screenHandler The screen handler associated with the closed inventory.
     */
    data class Close(val screenHandler: ScreenHandler) : Event

    /**
     * Represents an update event for an inventory, typically triggered when inventory contents or states
     * change during gameplay.
     *
     * @property revision The revision number indicating the current state of the inventory.
     * @property stacks A list of item stacks representing the contents of the inventory slots.
     * @property cursorStack The item stack currently held by the cursor.
     */
    data class FullUpdate(
        val revision: Int,
        val stacks: List<ItemStack>,
        val cursorStack: ItemStack,
    ) : Event

    /**
     * Represents an event triggered when a specific inventory slot is updated.
     *
     * @property syncId The synchronization ID related to the container or inventory.
     * @property revision The revision number representing the state of the inventory.
     * @property slot The index of the updated inventory slot.
     * @property stack The new item stack in the updated slot.
     */
    data class SlotUpdate(
        val syncId: Int,
        val revision: Int,
        val slot: Int,
        val stack: ItemStack
    ) : Event

    /**
     * Represents an event triggered when a player switches their selected hotbar slot.
     *
     * @property slot The index of the newly selected hotbar slot.
     */
    data class SelectedHotbarSlotUpdate(val slot: Int) : Event
}