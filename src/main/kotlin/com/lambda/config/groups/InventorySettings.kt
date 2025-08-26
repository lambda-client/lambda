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
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemUtils

class InventorySettings(
    c: Configurable,
    baseGroup: NamedEnum,
    vis: () -> Boolean = { true }
) : InventoryConfig, SettingGroup(c) {
    enum class Group(override val displayName: String) : NamedEnum {
        Container("Container"),
        Access("Access")
    }

    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, ItemUtils.defaultDisposables, "Items that will be ignored when checking for a free slot", register = false, vis).group(baseGroup, Group.Container).index()
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones", register = false, vis).group(baseGroup, Group.Container).index()
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from", register = false, vis).group(baseGroup, Group.Container).index()
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to", register = false, vis).group(baseGroup, Group.Container).index()

    override val accessShulkerBoxes by c.setting("Access Shulker Boxes", true, "Allow access to the player's shulker boxes", register = false, vis).group(baseGroup, Group.Access).index()
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest", register = false, vis).group(baseGroup, Group.Access).index()
    override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests", register = false, vis).group(baseGroup, Group.Access).index()
    override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes", register = false, vis).group(baseGroup, Group.Access).index()
}