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

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.interaction.inventory.ContainerSelection
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.Container
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.item.Item

interface InventoryConfig {
	val tickStageMask: Collection<TickEvent>
	val disposables: Collection<Item>
	val swapWithDisposables: Boolean
	val providerPriority: Priority
	val storePriority: Priority

	val accessShulkerBoxes: Boolean
	val accessChests: Boolean
	val accessEnderChest: Boolean
	val accessStashes: Boolean

	val containerSelection: ContainerSelection
		get() = ContainerSelection.selectContainer {
			val allowedContainers = buildSet {
				addAll(Container.Rank.entries)
				if (!accessShulkerBoxes) remove(Container.Rank.ShulkerBox)
				if (!accessEnderChest) remove(Container.Rank.EnderChest)
				if (!accessChests) remove(Container.Rank.Chest)
				if (!accessStashes) remove(Container.Rank.Stash)
			}
			ofAnyType(*allowedContainers.toTypedArray())
		}

	enum class Priority(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		WithMinItems("With Min Items", "Pick containers with the fewest matching items (or least space) first; useful for topping off or clearing leftovers."),
		WithMaxItems("With Max Items", "Pick containers with the most matching items (or most space) first; ideal for bulk moves with fewer transfers.");

		context(_: SafeContext)
		fun materialComparator(selection: StackSelection) =
			when (this) {
				WithMaxItems -> compareBy<Container> { it.rank }
					.thenByDescending { it.materialAvailable(selection) }
					.thenBy { it.name }

				WithMinItems -> compareBy<Container> { it.rank }
					.thenBy { it.materialAvailable(selection) }
					.thenBy { it.name }
			}

		context(_: SafeContext)
		fun spaceComparator(selection: StackSelection) =
			when (this) {
				WithMaxItems -> compareBy<Container> { it.rank }
					.thenByDescending { it.spaceAvailable(selection) }
					.thenBy { it.name }

				WithMinItems -> compareBy<Container> { it.rank }
					.thenBy { it.spaceAvailable(selection) }
					.thenBy { it.name }
			}
	}
}