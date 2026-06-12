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

package com.lambda.config.settings.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.util.item.ItemUtils

class InventorySettings(override val c: Config) : InventoryConfig, ConfigBlock {
    companion object {
        private const val CONTAINER_GROUP = "Container"
        private const val ACCESS_GROUP = "Access"
    }

    override val tickStageMask by c.setting("Inventory Stage Mask", ALL_STAGES.toSet(), description = "The sub-tick timing at which inventory actions are performed", displayClassName = true)
    @Group(CONTAINER_GROUP) override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, description = "Items that will be ignored when checking for a free slot")
    @Group(CONTAINER_GROUP) override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones")
    @Group(CONTAINER_GROUP) override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from")
    @Group(CONTAINER_GROUP) override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to")

    @Group(ACCESS_GROUP) override val accessShulkerBoxes by c.setting("Access Shulker Boxes", false, "Allow access to the player's shulker boxes")
    @Group(ACCESS_GROUP) override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests")
    @Group(ACCESS_GROUP) override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest")
    @Group(ACCESS_GROUP) override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes")
}