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
import com.lambda.task.TaskGenerator
import com.lambda.task.TaskOrNullGenerator
import com.lambda.task.tasks.AcquireStackTask.Companion.acquireStack
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollect
import com.lambda.task.tasks.NoopTask.Companion.noopTask
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.OpenContainerTask.Companion.openContainer
import com.lambda.task.tasks.PlaceContainerTask.Companion.placeContainer
import com.lambda.task.tasks.SimpleActionTask.Companion.simpleAction
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandlerType
import kotlin.comparisons.then

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
	override fun accessThen(
		closeAfter: Boolean,
		afterClose: TaskOrNullGenerator<Unit>?,
		afterOpen: TaskGenerator<Unit>
	) =
		with(automatedSafeContext) {
			if (!isAccessed) {
				acquireStack(Items.ENDER_CHEST.select()).then { slot ->
					placeContainer(slot).then { pos ->
						openContainer(pos).then {
							afterOpen(Unit).thenOrNull {
								if (closeAfter) {
									simpleAction("Close inventory") { player.closeHandledScreen() }.then {
										breakAndCollect(pos, lifeMaintenance = false).thenOrNull {
											afterClose?.invoke(this, Unit)
										}
									}
								} else null
							}
						}
					}
				}
			} else afterOpen(Unit).thenOrNull {
				if (closeAfter) {
					simpleAction("Close inventory") { mc.player?.closeHandledScreen() }.thenOrNull {
						afterClose?.invoke(this, Unit)
					}
				} else null
			}
		}

}
