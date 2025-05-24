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

import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import net.minecraft.block.Block

interface InventoryConfig {
    val disposables: Set<Block>
    val accessEnderChest: Boolean

    val swapWithDisposables: Boolean

    val providerPriority: Priority
    val storePriority: Priority

    enum class Priority {
        WithMinItems,
        WithMaxItems;

        fun materialComparator(selection: StackSelection) =
            when (this) {
                WithMaxItems -> compareBy<MaterialContainer> { it.rank }
                    .thenByDescending { it.materialAvailable(selection) }
                    .thenBy { it.name }

                WithMinItems -> compareBy<MaterialContainer> { it.rank }
                    .thenBy { it.materialAvailable(selection) }
                    .thenBy { it.name }
            }

        fun spaceComparator(selection: StackSelection) =
            when (this) {
                WithMaxItems -> compareBy<MaterialContainer> { it.rank }
                    .thenByDescending { it.spaceAvailable(selection) }
                    .thenBy { it.name }

                WithMinItems -> compareBy<MaterialContainer> { it.rank }
                    .thenBy { it.spaceAvailable(selection) }
                    .thenBy { it.name }
            }
    }
}
