
package com.minato.config.blocks

import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.interaction.material.ContainerSelection
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.Describable
import com.minato.util.NamedEnum
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
				addAll(MaterialContainer.Rank.entries)
				if (!accessShulkerBoxes) remove(MaterialContainer.Rank.ShulkerBox)
				if (!accessEnderChest) remove(MaterialContainer.Rank.EnderChest)
				if (!accessChests) remove(MaterialContainer.Rank.Chest)
				if (!accessStashes) remove(MaterialContainer.Rank.Stash)
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
				WithMaxItems -> compareBy<MaterialContainer> { it.rank }
					.thenByDescending { it.materialAvailable(selection) }
					.thenBy { it.name }

				WithMinItems -> compareBy<MaterialContainer> { it.rank }
					.thenBy { it.materialAvailable(selection) }
					.thenBy { it.name }
			}

		context(_: SafeContext)
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