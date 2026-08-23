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

package com.lambda.interaction.inventory.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.handler.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.select
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.task.tasks.AcquireStackTask.Companion.acquireStack
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollect
import com.lambda.task.tasks.OpenContainerTask.Companion.openContainer
import com.lambda.task.tasks.PlaceContainerTask.Companion.placeContainer
import com.lambda.task.tasks.SimpleActionTask.Companion.simpleAction
import com.lambda.task.wrappers.TaskOrNullSupplier
import com.lambda.task.wrappers.then
import com.lambda.task.wrappers.thenOrNull
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.math.BlockPos

object EnderChestContainer : Container(Rank.EnderChest), ExternalContainer {
	override val slots
		get() =
			if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
			else emptyList()
	override var stacks = emptyList<ItemStack>()

	override val description = buildText { literal("Ender Chest") }

	override val isAccessed
		get() =
			lastInteractedBlockEntity?.let { blockEntity ->
				blockEntity is EnderChestBlockEntity &&
						mc.player?.currentScreenHandler?.type == ScreenHandlerType.GENERIC_9X3
			} ?: false

	context(automatedSafeContext: AutomatedSafeContext)
	override fun <R> accessThen(
		closeAfter: Boolean,
		afterOpen: TaskOrNullSupplier<Unit, R?>,
		afterClose: TaskOrNullSupplier<R?, *>
	) =
		with(automatedSafeContext) {
			taskOrSkipOrNull(
				optional = {
					if (isAccessed) null
					else {
						wrappedTask<BlockPos>("Setup Ender Chest") { success ->
							acquireStack(Items.ENDER_CHEST.select(1)).then { slot ->
								placeContainer(slot).then { pos ->
									openContainer(pos).finally {
										success(pos)
									}
								}
							}
						}
					}
				}
			) { pos ->
				taskOrSkipOrNull({ afterOpen(Unit) }) { afterOpenResult ->
					if (closeAfter) {
						simpleAction("Close inventory") { player.closeHandledScreen() }.thenOrNull {
							taskOrSkipOrNull(
								optional = {
									if (pos != null) breakAndCollect(pos)
									else null
								}
							) { afterClose(afterOpenResult) }
						}
					} else null
				}
			}
		}

}
