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
import com.lambda.config.blocks.ItemPickupConfig
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.toStructure
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.count
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainers
import com.lambda.interaction.handler.handlers.ContainerHandler.spaceLeft
import com.lambda.interaction.handler.handlers.ContainerHandler.stacksAvailable
import com.lambda.interaction.handler.handlers.SlotActionProgressHandler
import com.lambda.interaction.manager.managers.breaking.breakRequest
import com.lambda.interaction.manager.managers.interacting.interactRequest
import com.lambda.task.Task
import com.lambda.task.TaskMarker
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.concurrent.ConcurrentLinkedQueue

@TaskMarker
context(automated: Automated)
fun farmEnderChests(
	stopAfterMined: Int = -1,
	stopOnFull: Boolean = true,
	restockCount: Int = 64,
	awaitExternalDependencies: Boolean = true,
	dropPickupConfig: ItemPickupConfig = ItemPickupConfig.DEFAULT
) = FarmEnderChestsTask(
	stopAfterMined,
	stopOnFull,
	restockCount,
	awaitExternalDependencies,
	dropPickupConfig,
	automated
)

class FarmEnderChestsTask @TaskMarker internal constructor(
	private val stopAfterMined: Int = -1,
	private val stopIfFull: Boolean = true,
	private val restockCount: Int = 64,
	private val awaitExternalDependencies: Boolean = true,
	private val dropPickupConfig: ItemPickupConfig = ItemPickupConfig.DEFAULT,
	automated: Automated
) : Task<Unit>(), Automated by automated {
	override val name = "Farming Ender Chests"

	private var pos: BlockPos? = null
	private var startObsidian = 0
	private var broken = 0
	private val drops = mutableSetOf<ItemEntity>()

	companion object {
		private val OBI_SELECTION = Items.OBSIDIAN.select(StackSelection.UNBOUNDED)
		private val ECHEST_SELECTION = stackSelection(1) { isItem(Items.ENDER_CHEST); alwaysWholeStacks() }
		private val SINGLE_ECHEST_SELECTION = Items.ENDER_CHEST.select(1)
	}

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
		updateFarmPos()
		startObsidian = count(OBI_SELECTION, ContainerSelection.PLAYER)
	}

	init {
		listen<TickEvent.Input.Post> {
			drops.removeIf { it.isRemoved }

			val pos = pos
				?: run {
					failure("Farm position null")
					return@listen
				}
			val state = blockState(pos)

			if (!state.breakReady) {
				if (stopIfFull) {
					val obiSpaceLeft = spaceLeft(OBI_SELECTION, ContainerSelection.HOTBAR_AND_INVENTORY)
					if (obiSpaceLeft < 8) {
						val containers = findContainers(ContainerSelection.HOTBAR_AND_INVENTORY)
						if (awaitExternalDependencies && SlotActionProgressHandler.isPushHandled(containers)) return@listen
						success()
						return@listen
					}
				}

				if (dropPickupConfig.shouldPickup(drops)) {
					pickup()
					return@listen
				}

				if (stopAfterMined in 0..broken) {
					if (drops.isNotEmpty() && dropPickupConfig.pickupDrops) {
						pickup()
						return@listen
					}
					success()
					return@listen
				}

				if (!state.echestFarmable) {
					failure("Farm position blocked")
					return@listen
				}

				val enderChestsAvailable =
					stacksAvailable(
						SINGLE_ECHEST_SELECTION,
						ContainerSelection.HOTBAR_AND_INVENTORY
					)
				if (!enderChestsAvailable) {
					if (restockCount <= 0) {
						failure("No Ender Chests available to place")
						return@listen
					}

					transfer(
						fromStack = Items.ENDER_CHEST.select(restockCount),
						toSelection = ContainerSelection.HOTBAR_AND_INVENTORY,
						toStack = stackSelection { withoutSelector(OBI_SELECTION) }
					).start()
					return@listen
				}

				val enderChestInHotbar =
					stacksAvailable(
						SINGLE_ECHEST_SELECTION,
						HotbarContainer.select()
					)
				if (!enderChestInHotbar) {
					transfer(
						ECHEST_SELECTION,
						InventoryContainer.select(),
						HotbarContainer.select()
					).start()
					return@listen
				}
			}

			runSafeAutomated {
				if (state.placeReady) {
					val placeRequest = placeRequest(pos)
					if (placeRequest == null) {
						updateFarmPos()
						placeRequest(pos)
					}
				}

				if (blockState(pos).breakReady) {
					pos.toStructure(TargetState.Empty)
						.sim()
						.breakRequest(pendingInteractions) {
							onStop { broken++ }
							onReBreak { broken++ }
							onItemDrop {
								if (dropPickupConfig.pickupDrops) {
									drops.add(it)
								}
							}
						}
						?.submit(true)
				}
			}
		}
	}

	private fun AutomatedSafeContext.placeRequest(pos: BlockPos) =
		pos.toStructure(TargetState.Block(Blocks.ENDER_CHEST))
			.sim()
			.interactRequest(pendingInteractions)
			?.submit(true)

	private fun pickup() {
		pickupItems(drops)
			.softFail()
			.start()
	}

	private fun SafeContext.updateFarmPos() {
		pos = potentialFarmPositions.firstOrNull { blockState(it).echestFarmable }
			?: run {
				failure("All potential farm positions exhausted")
				return
			}
	}
}