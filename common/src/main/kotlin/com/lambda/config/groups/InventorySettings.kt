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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.util.item.ItemUtils

class InventorySettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : InventoryConfig {
    override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, "Items that will be ignored when checking for a free slot", vis)
    override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest", vis)
    override val actionTimout by c.setting("Action Timeout", 10, 0..100, 1, "How long to wait for after each inventory action", " ticks", vis)
    override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones", vis)
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from", vis)
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to", vis)
}