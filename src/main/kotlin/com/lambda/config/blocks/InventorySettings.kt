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

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.container.ContainerType
import com.lambda.util.item.ItemUtils

class InventorySettings(override val c: Config) : InventoryConfig, ConfigBlock {
    companion object {
        const val ENDER_CHEST_GROUP = "Ender Chest"
    }

    override val tickStageMask by c.setting("Inventory Stage Mask", ALL_STAGES.toSet(), description = "The sub-tick timing at which inventory actions are performed", displayClassName = true)
    override val disposables by c.setting("Disposables", ItemUtils.DEFAULT_DISPOSABLES, description = "Items that will be ignored when checking for a free slot")
    override val accessPriority by c.setting("Access Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from")
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to")
    override val allowedContainers by c.setting("Allowed Containers", ContainerType.entries, description = "What containers are accessible")

    @Group(ENDER_CHEST_GROUP) override val enderChestSearchRadius by c.setting("Initial Search Radius", 10, 0..20, unit = " blocks", description = "Initial search radius to scan for an already placed ender chest. If one isn't found, one will be placed. If an ender chest isn't found in the inventory, it scans the rest of the loaded area to find an already placed one")
}