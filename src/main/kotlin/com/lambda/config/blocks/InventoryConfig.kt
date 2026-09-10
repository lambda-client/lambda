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

import com.lambda.event.events.TickEvent
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.containerSelection
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.item.Item

interface InventoryConfig {
	val tickStageMask: Collection<TickEvent>
	val disposables: Collection<Item>

	val allowedContainers: Collection<com.lambda.interaction.container.ContainerType>
	val containerSelection: ContainerSelection
		get() = containerSelection { ofAnyType(*allowedContainers.toTypedArray()) }

	val enderChestSearchRadius: Int

	@Suppress("unused")
	enum class ContainerPriority(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		WithMinItems("With Min Items", "Pick containers with the fewest matching items (or least space) first; useful for topping off or clearing leftovers."),
		WithMaxItems("With Max Items", "Pick containers with the most matching items (or most space) first; ideal for bulk moves with fewer transfers.");

		fun <T> comparator(countSelector: (Container, T) -> Int) =
			compareBy<Pair<Container, T>> { it.first.type }
				.thenByDescending { countSelector(it.first, it.second) }
				.thenBy { it.first.name }
	}
}