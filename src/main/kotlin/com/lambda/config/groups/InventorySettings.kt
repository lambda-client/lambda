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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.interaction.request.inventory.InventoryConfig.Priority
import com.lambda.util.item.ItemUtils

class InventorySettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : InventoryConfig {
    val page by c.setting("Inventory Page", Page.Container, "The page to open when the module is enabled", vis)

    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, "Items that will be included when checking for a free slot / are allowed to be droped when inventory is full") { vis() && page == Page.Container}
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones") { vis() && page == Page.Container}
    override val providerPriority by c.setting("Provider Priority", Priority.WithMinItems, "What container to prefer when retrieving the item from") { vis() && page == Page.Container}
    override val storePriority by c.setting("Store Priority", Priority.WithMinItems, "What container to prefer when storing the item to") { vis() && page == Page.Container}

    override val accessShulkerBoxes by c.setting("Access Shulker Boxes", true, "Allow access to the player's shulker boxes") { vis() && page == Page.Access}
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest") { vis() && page == Page.Access}
    override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests") { vis() && page == Page.Access}
    override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes") { vis() && page == Page.Access}

    enum class Page {
        Container, Access, Tools
    }
}
