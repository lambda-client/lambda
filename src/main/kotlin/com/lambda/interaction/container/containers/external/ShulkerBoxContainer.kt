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

package com.lambda.interaction.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.ExternalContainer
import com.lambda.interaction.container.NestedContainer
import com.lambda.interaction.container.OpenContainerTask
import com.lambda.interaction.container.OpenedContainerContext
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.findSlot
import com.lambda.interaction.handler.handlers.ContainerSearchScope
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.breakAndCollect
import com.lambda.task.tasks.openContainer
import com.lambda.task.tasks.placeContainer
import com.lambda.task.tasks.transfer
import com.lambda.task.tasks.wrappers.actionTask
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import java.util.Objects.hash

data class ShulkerBoxContainer(
	val itemName: String,
	val shulkerItem: Item,
	override var stacks: List<ItemStack>,
	override val containedIn: Container,
	override val slotIdentity: SlotIdentity,
) : NestedContainer(ContainerType.ShulkerBox), ExternalContainer {
	override val slots
		get(): List<Slot> =
			if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
			else emptyList()

	override val description =
		buildText {
			highlighted(itemName)
			literal(" in ")
			highlighted(containedIn.name)
			literal(" in slot index ${slotIdentity.index}")
		}

	override val isAccessed = false

	@Ta5kBuilder
	context(automated: Automated)
	override fun access() = OpenShulkerBoxTask(automated)

	inner class OpenShulkerBoxTask @Ta5kBuilder internal constructor(
		automated: Automated
	) : OpenContainerTask<OpenedShulkerBoxContext>(description), Automated by automated {
		override fun SafeContext.onStart() {
			val selection = stackSelection {
				isItem(shulkerItem)
				withName(itemName)
				sortedBySlotMatch(slotIdentity)
				sortedByBestContentMatch(stacks)
			}
			taskOrNull {
				if (containedIn == HotbarContainer) null
				else selection.transfer(containedIn.select(ContainerSearchScope.Loaded), HotbarContainer.select())
			}.then { result ->
				placeContainer(findSlot(result?.stackSelection ?: selection, HotbarContainer.select()))
			}.then { pos ->
				openContainer<PlacedShulkerBoxContainer>(pos).onSuccess {
					success(OpenedShulkerBoxContext(it))
				}
			}.start()
		}
	}

	inner class OpenedShulkerBoxContext(
		override val container: PlacedShulkerBoxContainer,
	) : OpenedContainerContext {
		context(automated: Automated)
		override fun close(): Task<*> {
			return taskOrNull {
				if (!container.isAccessed) null
				else actionTask { player.closeHandledScreen() }
			}.then {
				breakAndCollect(container.pos)
			}.thenOrNull { outcome ->
				val pickedUp = outcome.pickups
					.asSequence()
					.filter { it.stack.item == shulkerItem && it.stack.name.string == itemName }
					.firstNotNullOfOrNull { it.slot }
				val inContainer = pickedUp != null && containedIn.slots.any { it.identity() == pickedUp }
				if (inContainer && !slotIdentity.isEmpty()) return@thenOrNull null

				stackSelection {
					isItem(shulkerItem)
					withName(itemName)
					if (pickedUp != null) predicate { _, slot -> slot?.identity() == pickedUp }
					sortedByBestContentMatch(container.stacks)
					sortedByRecentChange()
				}.transfer(
					ContainerSelection.HOTBAR_AND_INVENTORY,
					containedIn.select(ContainerSearchScope.Loaded),
					stackSelection {
						isEmpty()
						sortedByMatches {
							stackSelection {
								predicate { _, slot -> slot?.identity() == slotIdentity }
							}
						}
					},
					allowQuickMove = false
				).softFail()
			}
		}
	}

	override fun hashCode() = hash(containedIn.hashCode(), slotIdentity)

	override fun equals(other: Any?) =
		other is ShulkerBoxContainer &&
				containedIn == other.containedIn &&
				slotIdentity == other.slotIdentity
}