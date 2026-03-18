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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

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
        val stack: ItemStack,
    ) : Event

	/**
	 * Represents an action performed on an inventory slot, such as clicking or interacting with it.
	 */
	open abstract class SlotAction : Event, ICancellable {
		/**
		 * Represents a click action performed on an inventory slot.
		 *
		 * This event provides detailed information about the click action, including the screen handler,
		 * the slot involved, the button used, and the type of action performed.
		 *
		 * This event is [Cancellable], allowing handlers to prevent the default click behavior if necessary.
		 *
		 * @property screenHandler The screen handler managing the inventory interaction.
		 * @property slot The slot that was clicked, or null if no slot was involved.
		 * @property slotId The ID of the slot that was clicked.
		 * @property button The mouse button used for the click action.
		 * @property actionType The type of action performed on the slot.
		 */
		data class Click(
			val screenHandler: ScreenHandler,
			val slot: Slot?,
			val slotId: Int,
			val button: Int,
			val actionType: SlotActionType,
		) : ICancellable by Cancellable()
	}

    abstract class HotbarSlot : Event {
        /**
         * Represents an event triggered when the client attempts to send slot update to the server.
         *
         * Updated slot id will come to the server if it defers from last reported slot.
         */
        data class Update(val slot: Int) : HotbarSlot()

        /**
         * Represents an event triggered when the server forces the player to change active hotbar slot.
         * (world load or anticheat flag).
         *
         * This event is [Cancellable], you may ignore the server with risk
         * of unexpected behaviour depending on the strictness of the server/anticheat.
         *
         * @property slot The index of the newly selected hotbar slot.
         */
        data class Sync(val slot: Int) : HotbarSlot(), ICancellable by Cancellable()
    }
}
