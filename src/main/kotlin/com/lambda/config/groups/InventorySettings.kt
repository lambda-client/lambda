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
import com.lambda.config.SettingGroup
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.managers.inventory.InventoryConfig
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemUtils

class InventorySettings(
    c: Configurable,
    baseGroup: NamedEnum,
    override val visibility: () -> Boolean = { true },
) : SettingGroup(c), InventoryConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Container("Container"),
        Access("Access")
    }

    override val actionsPerSecond by c.setting("Actions Per Second", 100, 0..100, 1, "How many inventory actions can be performed per tick", visibility = visibility).group(baseGroup, Group.General).index()
    override val tickStageMask by c.setting("Inventory Stage Mask", ALL_STAGES.toSet(), description = "The sub-tick timing at which inventory actions are performed", displayClassName = true, visibility = visibility).group(baseGroup, Group.General).index()
    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, description = "Items that will be ignored when checking for a free slot", visibility = visibility).group(baseGroup, Group.Container).index()
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones", visibility = visibility).group(baseGroup, Group.Container).index()
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from", visibility = visibility).group(baseGroup, Group.Container).index()
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to", visibility = visibility).group(baseGroup, Group.Container).index()

    override val accessShulkerBoxes by c.setting("Access Shulker Boxes", false, "Allow access to the player's shulker boxes", visibility = visibility).group(baseGroup, Group.Access).index()
    override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests", visibility = visibility).group(baseGroup, Group.Access).index()
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest", visibility = visibility).group(baseGroup, Group.Access).index()
    override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes", visibility = visibility).group(baseGroup, Group.Access).index()
}