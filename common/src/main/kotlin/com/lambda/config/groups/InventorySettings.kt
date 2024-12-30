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

class InventorySettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : InventoryConfig {
    override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WITH_MIN_ITEMS, "What container to prefer when retrieving the item from", vis)
    override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WITH_MIN_ITEMS, "What container to prefer when storing the item to", vis)
}