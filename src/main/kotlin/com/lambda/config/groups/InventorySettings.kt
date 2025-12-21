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
    baseGroup: NamedEnum
) : SettingGroup(c), InventoryConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Container("Container"),
        Access("Access")
    }

    override val actionsPerSecond by c.setting("Actions Per Second", 100, 0..100, 1, "How many inventory actions can be performed per tick").group(baseGroup, Group.General).index()
    override val tickStageMask by c.setting("Inventory Stage Mask", ALL_STAGES.toSet(), description = "The sub-tick timing at which inventory actions are performed").group(baseGroup, Group.General).index()
    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, description = "Items that will be ignored when checking for a free slot").group(baseGroup, Group.Container).index()
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones").group(baseGroup, Group.Container).index()
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from").group(baseGroup, Group.Container).index()
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to").group(baseGroup, Group.Container).index()

    override val immediateAccessOnly by c.setting("Immediate Access Only", false, "Only allow access to inventories that can be accessed immediately").group(baseGroup, Group.Access).index()
    override val accessShulkerBoxes by c.setting("Access Shulker Boxes", true, "Allow access to the player's shulker boxes") { !immediateAccessOnly }.group(baseGroup, Group.Access).index()
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest") { !immediateAccessOnly }.group(baseGroup, Group.Access).index()
    override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests") { !immediateAccessOnly }.group(baseGroup, Group.Access).index()
    override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes") { !immediateAccessOnly }.group(baseGroup, Group.Access).index()
}