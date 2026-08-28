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
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.handler.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.OpenContainerTask
import com.lambda.interaction.inventory.container.OpenedContainerContext
import com.lambda.interaction.inventory.select
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.acquireStack
import com.lambda.task.tasks.breakAndCollect
import com.lambda.task.tasks.findBlock
import com.lambda.task.tasks.openContainer
import com.lambda.task.tasks.placeContainer
import com.lambda.task.tasks.wrappers.actionTask
import com.lambda.task.tasks.wrappers.successTask
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.withBranch
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.Blocks
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
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

	@Ta5kBuilder
	context(automated: Automated)
	override fun access() = OpenEnderChestContainerTask(automated)

	class OpenEnderChestContainerTask @Ta5kBuilder internal constructor(
		automated: Automated
	) : OpenContainerTask<OpenedEnderChestContext>(description), Automated by automated {
		override fun SafeContext.onStart() {
			taskOrNull {
				if (isAccessed) null
				else findBlock(Blocks.ENDER_CHEST, inventoryConfig.enderChestSearchRadius).withBranch(
					onSuccess = { pos ->
						openContainer(pos).onSuccess { sh ->
							success(OpenedEnderChestContext(sh, pos, false))
						}
					},
					onFailure = {
						acquireStack(Items.ENDER_CHEST.select(1))
							.then { placeContainer(it) }
							.then { pos ->
								openContainer(pos).onSuccess { sh ->
									success(OpenedEnderChestContext(sh, pos, true))
								}
							}
					}
				)
			}.execute(this@OpenEnderChestContainerTask)
		}
	}

	class OpenedEnderChestContext(
		val screenHandler: ScreenHandler,
		val blockPos: BlockPos,
		val placed: Boolean
	) : OpenedContainerContext {
		context(_: Automated)
		override fun close() =
			taskOrNull {
				if (isAccessed) actionTask { player.closeScreen() }
				else null
			}.then {
				if (placed) breakAndCollect(blockPos)
				else successTask()
			}
	}
}
