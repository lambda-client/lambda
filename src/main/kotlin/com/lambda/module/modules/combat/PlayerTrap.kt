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

package com.lambda.module.modules.combat

import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.editTypedSettings
import com.lambda.config.hideBlock
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.selectStack
import com.lambda.interaction.handler.handlers.FriendHandler.isFriend
import com.lambda.interaction.handler.handlers.findContainer
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.build
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.shrinkByEpsilon
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.math.blockPos
import com.lambda.util.world.entitySearch
import net.minecraft.block.Blocks
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.util.math.BlockPos
import kotlin.jvm.optionals.getOrNull

object PlayerTrap : Module(
	name = "PlayerTrap",
	description = "Surrounds players with any given block",
	tag = ModuleTag.COMBAT
) {
	private val blocks by setting("Blocks", setOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN))
	private val friends by setting("Friends", false)
	private val self by setting("Self", false)

	private var task: Task<*>? = null

	init {
		setDefaultAutomationConfig()
			.withEdits {
				buildConfig.apply {
					editTypedSettings(
						::pathing,
						::spleefEntities,
						::collectDrops
					) { defaultValue(false); hide() }
				}
				hideBlock(::eatConfig)
			}

		onEnable {
			task = tickingBlueprint {
				val selection =
					selectStack {
						custom { stack, _ ->
							stack.item is BlockItem && blocks.contains(stack.item.block)
						}
					}

				val block = findContainer(selection, ContainerSelection.HOTBAR_AND_INVENTORY)
					?.findStack(selection)
					?.item?.block
					?: return@tickingBlueprint emptyMap()

				val targetPlayer =
					if (self) player
					else entitySearch<OtherClientPlayerEntity>(
						buildConfig.blockReach,
						player.eyePos.blockPos
					).firstOrNull { friends || !isFriend(it.gameProfile) }
						?: return@tickingBlueprint emptyMap()

				getTrapPositions(targetPlayer).associateWith { TargetState.Block(block) }
			}.build(finishOnDone = false)
				.start()
		}
		onDisable { task?.cancel(); task = null }
	}

	fun SafeContext.getTrapPositions(player: PlayerEntity): Set<BlockPos> {
		val min = player.boundingBox.shrinkByEpsilon().minPos.blockPos.add(-1, -1, -1)
		val max = player.boundingBox.shrinkByEpsilon().maxPos.blockPos.add(1, 1, 1)

		return buildSet {
			(min.x + 1..<max.x).forEach { x ->
				(min.y + 1..<max.y).forEach { y ->
					add(BlockPos(x, y, min.z))
					add(BlockPos(x, y, max.z))
				}
			}

			(min.z + 1..<max.z).forEach { z ->
				(min.y + 1..<max.y).forEach { y ->
					add(BlockPos(min.x, y, z))
					add(BlockPos(max.x, y, z))
				}
			}

			(min.x + 1..<max.x).forEach { x ->
				(min.z + 1..<max.z).forEach { z ->
					BlockPos(x, min.y, z).let { pos ->
						if (pos != player.supportingBlockPos.getOrNull() || blockState(pos).isReplaceable) add(pos)
					}
					add(BlockPos(x, max.y, z))
				}
			}
		}
	}
}