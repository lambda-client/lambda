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

package com.lambda.task.tasks

import com.lambda.config.blocks.BreakConfig
import com.lambda.config.blocks.BreakConfig.BreakConfirmationMode
import com.lambda.config.blocks.InteractConfig
import com.lambda.config.blocks.InteractConfig.InteractConfirmationMode
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainers
import com.lambda.interaction.manager.managers.breaking.breakRequest
import com.lambda.interaction.manager.managers.interacting.interactRequest
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.concurrent.ConcurrentLinkedQueue

@Ta5kBuilder
context(automated: Automated)
fun farmEnderChests(
	stopAfterMined: Int = -1,
	stopOnFull: Boolean = true,
	pathfind: Boolean = true,
) = FarmEnderChestsTask(
	stopAfterMined,
	stopOnFull,
	pathfind,
	automated
)

class FarmEnderChestsTask @Ta5kBuilder internal constructor(
	val stopAfterMined: Int = -1,
	val stopIfFull: Boolean = true,
	pathfind: Boolean = true,
	automated: Automated
) : Task<Unit>(), Automated by automated {
	override val name = "Farming ender chests"

	private var pos: BlockPos? = null
	private var startObsidian = 0
	private var broken = 0
	private val drops = mutableSetOf<ItemEntity>()

	private val obiSelection = Items.OBSIDIAN.select(StackSelection.UNBOUNDED)

	private val BlockState.echestFarmable get() = placeReady || breakReady
	private val BlockState.placeReady get() = isReplaceable
	private val BlockState.breakReady get() = isOf(Blocks.ENDER_CHEST)

	context(safeContext: SafeContext)
	private val potentialFarmPositions
		get() = with(safeContext) {
			sequence {
				Direction.entries.forEach { dir ->
					if (dir.axis.isVertical) return@forEach
					yield(player.blockPos.offset(dir))
				}
			}
		}

	private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

	//ToDo: Make these more easily configurable, breakConfirmation specifically
	override val breakConfig =
		object : BreakConfig by automated.breakConfig {
			override val forceSilkTouch = false
			override val avoidSilkTouch = true
			override val breakConfirmation = BreakConfirmationMode.None
		}

	override val interactConfig =
		object : InteractConfig by automated.interactConfig {
			override val interactConfirmation = InteractConfirmationMode.None
		}

	override fun SafeContext.onStart() {
		pos = potentialFarmPositions.first { blockState(it).echestFarmable }
			?: run {
				failure("All potential farm positions exhausted")
				return
			}
		startObsidian = findContainers(ContainerSelection.PLAYER)
			.sumOf { it.count(obiSelection) }
	}

	init {
		listen<TickEvent.Input.Post> {
			drops.removeIf { it !in world.entities }

			if (stopIfFull) {
				val obiSpaceLeft = findContainers(ContainerSelection.HOTBAR_AND_INVENTORY)
					.sumOf { it.spaceLeft(obiSelection) }
				if (obiSpaceLeft < 8) success()
			}

			if (stopAfterMined in 0..broken) {
				if (drops.isNotEmpty()) return@listen
				success()
			}

			val pos = pos
				?: run {
					failure("Farm position null")
					return@listen
				}
			val state = blockState(pos)

			if (!state.echestFarmable) {
				failure("Farm position blocked")
				return@listen
			}

			runSafeAutomated {
				if (state.placeReady) {
					pos.toStructure(TargetState.Block(Blocks.ENDER_CHEST))
						.sim()
						.interactRequest(pendingInteractions)
						?.submit(true)
				}

				if (blockState(pos).breakReady) {
					pos.toStructure(TargetState.Empty)
						.sim()
						.breakRequest(pendingInteractions) {
							onStop { broken++ }
							onItemDrop { drops.add(it) }
						}
						?.submit(true)
				}
			}
		}
	}
}