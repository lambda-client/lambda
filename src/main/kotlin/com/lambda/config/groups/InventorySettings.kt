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
import com.lambda.event.events.TickEvent
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemUtils

class InventorySettings(
    c: Configurable,
    baseGroup: NamedEnum,
    vis: () -> Boolean = { true }
) : InventoryConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Container("Container"),
        Access("Access")
    }

    override val actionsPerSecond by c.setting("Actions Per Second", 100, 0..100, 1, "How many inventory actions can be performed per tick", visibility = vis).group(baseGroup, Group.General)
    override val tickStageMask by c.setting("Inventory Stage Mask", setOf<TickEvent>(TickEvent.Pre, TickEvent.Input.Pre, TickEvent.Input.Post, TickEvent.Player.Post), description = "The sub-tick timing at which inventory actions are performed", visibility = vis).group(baseGroup, Group.General)
    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, ItemUtils.defaultDisposables, "Items that will be ignored when checking for a free slot", vis).group(baseGroup, Group.Container)
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones", vis).group(baseGroup, Group.Container)
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from", vis).group(baseGroup, Group.Container)
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to", vis).group(baseGroup, Group.Container)

    override val accessShulkerBoxes by c.setting("Access Shulker Boxes", true, "Allow access to the player's shulker boxes", vis).group(baseGroup, Group.Access)
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest", vis).group(baseGroup, Group.Access)
    override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests", vis).group(baseGroup, Group.Access)
    override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes", vis).group(baseGroup, Group.Access)
}