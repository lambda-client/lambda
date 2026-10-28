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

package com.lambda.module.modules.world

import com.lambda.config.Group
import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.editSetting
import com.lambda.config.editTypedSettings
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.results.GenericResult
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.moveStack
import com.lambda.interaction.manager.managers.breaking.breakRequest
import com.lambda.interaction.manager.managers.interacting.interactRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.threading.withSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.EnchantmentUtils.getEnchantment
import com.lambda.util.item.ItemStackUtils.emptySpace
import com.lambda.util.item.ItemStackUtils.hasSpace
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("unused")
object EnderChestFarmer : Module(
	name = "EnderChestFarmer",
	description = "Farms obsidian using ender chests",
	tag = ModuleTag.WORLD
) {
	private const val RENDER_GROUP = "Render"

	@Group(RENDER_GROUP) private val renderFarmPos by setting("Render Farm Pos", true, "Outlines the position ender chests are placed and broken at")
	@Group(RENDER_GROUP) private val farmPosColor by setting("Farm Pos Color", Color(155, 0, 255), "The outline color of the farm position") { renderFarmPos }

	private val pendingActions = ConcurrentLinkedQueue<BuildContext>()

	private var farmPos: BlockPos? = null
	private var startObsidian = 0

	private val SafeContext.obsidianCount
		get() = player.hotbarAndInventoryStacks.filter { it.item == Items.OBSIDIAN }.sumOf { it.count }

	private val SafeContext.inventoryFull
		get() = player.hotbarAndInventoryStacks.let { stacks ->
			stacks.emptySpace == 0 && stacks.none { it.item == Items.OBSIDIAN && it.hasSpace }
		}

	private val SafeContext.hasEnderChests
		get() = Items.ENDER_CHEST.select().filter(player.hotbarAndInventoryStacks).isNotEmpty()

	private val SafeContext.missingPickaxe: String?
		get() {
			val silkTouch = when {
				breakConfig.avoidSilkTouch -> false
				breakConfig.forceSilkTouch -> true
				else -> null
			}
			val found = player.hotbarAndInventoryStacks.any { stack ->
				stack.isSuitableFor(Blocks.ENDER_CHEST.defaultState) &&
						(silkTouch == null || (stack.getEnchantment(Enchantments.SILK_TOUCH) > 0) == silkTouch)
			}
			return if (found) null
			else when (silkTouch) {
				true -> "You need a pickaxe with Silk Touch"
				false -> "You need a pickaxe without Silk Touch"
				null -> "You need a pickaxe"
			}
		}

	private val BlockState.farmable
		get() = isOf(Blocks.ENDER_CHEST) || isReplaceable

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::buildConfig, ::breakConfig, ::interactConfig, ::rotationConfig, ::hotbarConfig, ::inventoryConfig)
				buildConfig.apply {
					editTypedSettings(::pathing, ::spleefEntities) {
						defaultValue(false)
						hide()
					}
				}
				breakConfig.apply {
					::rebreak.editSetting {
						defaultValue(true)
						hide()
					}
					::avoidSilkTouch.editSetting { defaultValue(true) }
					editTypedSettings(::doubleBreak, ::avoidFluids, ::avoidSupporting) { defaultValue(false) }
				}
				hotbarConfig::keepTicks.editSetting { defaultValue(0) }
			}

		onEnable {
			val pos = player.blockPos.offset(player.horizontalFacing)
			if (!blockState(pos).farmable) {
				this@EnderChestFarmer.logError("The block in front of you must be air or an ender chest")
				disable()
				return@onEnable
			}
			missingPickaxe?.let {
				this@EnderChestFarmer.logError(it)
				disable()
				return@onEnable
			}
			farmPos = pos
			startObsidian = obsidianCount
		}

		onDisable {
			this@EnderChestFarmer.info("Mined ${obsidianCount - startObsidian} obsidian")
			farmPos = null
			pendingActions.clear()
		}

		listen<TickEvent.Input.Post> {
			val pos = farmPos ?: return@listen
			if (inventoryFull) {
				this@EnderChestFarmer.info("Inventory is full")
				disable()
				return@listen
			}
			missingPickaxe?.let {
				this@EnderChestFarmer.logError(it)
				disable()
				return@listen
			}
			val state = blockState(pos)
			if (!state.farmable) {
				this@EnderChestFarmer.logError("The farm position is blocked")
				disable()
				return@listen
			}
			withSafeAutomated {
				if (state.isOf(Blocks.ENDER_CHEST)) breakEnderChest(pos)
				else placeEnderChest(pos)
			}
		}

		tickedRenderer("EnderChestFarmer Ticked Renderer") {
			if (!renderFarmPos) return@tickedRenderer
			farmPos?.let { pos ->
				box(pos) {
					hideFill()
					outlineColor(farmPosColor)
				}
			}
		}
	}

	private fun AutomatedSafeContext.breakEnderChest(pos: BlockPos) {
		val results = pos.toStructure(TargetState.Empty).sim()
		if (transferMissingItem(results)) return
		results.breakRequest(pendingActions)?.submit()
	}

	private fun AutomatedSafeContext.placeEnderChest(pos: BlockPos) {
		if (!hasEnderChests) {
			this@EnderChestFarmer.info("Out of ender chests")
			disable()
			return
		}
		val results = pos.toStructure(TargetState.Block(Blocks.ENDER_CHEST)).sim()
		if (transferMissingItem(results)) return
		results.interactRequest(pendingActions)?.submit()
	}

	private fun AutomatedSafeContext.transferMissingItem(results: Set<BuildResult>): Boolean {
		val wrongItem = results
			.map { if (it is Dependent) it.lastDependency else it }
			.filterIsInstance<GenericResult.WrongItemSelection>()
			.firstOrNull() ?: return false
		wrongItem.neededSelection.moveStack(toSelection = HotbarContainer.select())
		return true
	}
}
