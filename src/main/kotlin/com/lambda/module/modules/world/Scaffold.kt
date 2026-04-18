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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.config.settings.complex.Bind
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.interacting.InteractConfig
import com.lambda.interaction.managers.interacting.InteractRequest.Companion.interactRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.item.ItemUtils.blockItem
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

object Scaffold : Module(
	name = "Scaffold",
	description = "Places blocks under the player",
	tag = ModuleTag.WORLD,
) {
	private val blacklistedBlocks by setting("Blacklisted Blocks", mutableSetOf<Block>())
	private val bridgeRange by setting("Bridge Range", 5, 0..5, 1, "The range at which blocks can be placed to help build support for the player", unit = " blocks")
	private val onlyBelow by setting("Only Below", true, "Restricts bridging to only below the player to avoid place spam if it's impossible to reach the supporting position") { bridgeRange > 0 }
	private val descend by setting("Descend", Bind.EMPTY, "Lower the place position by one to allow the player to lower y level")
	private val descendAmount by setting("Descend Amount", 1, 1..5, 1, "The amount to lower the place position by when descending", unit = " blocks") { descend != Bind.EMPTY }

	private val pendingActions = ConcurrentLinkedQueue<BuildContext>()

	init {
		setDefaultAutomationConfig {
			applyEdits {
				buildConfig.apply {
					editTyped(::pathing, ::stayInRange, ::collectDrops, ::spleefEntities) {
						defaultValue(false)
						hide()
					}
					::checkSideVisibility.edit { defaultValue(true) }
					hide(::inventoryLimit, ::breakBlocks)
				}
				interactConfig::airPlace.edit { defaultValue(InteractConfig.AirPlaceMode.None) }
				rotationConfig.apply {
					::instant.edit { defaultValue(false) }
					::mean.edit { defaultValue(120.0) }
					::spread.edit { defaultValue(0.0) }
				}
				inventoryConfig.apply {
					hide(
						::tickStageMask,
						::swapWithDisposables,
						::providerPriority,
						::storePriority,
						::accessShulkerBoxes,
						::accessEnderChest,
						::accessChests,
						::accessStashes,
						::disposables
					)
				}
				hideAllGroupsExcept(buildConfig, interactConfig, rotationConfig, hotbarConfig, inventoryConfig)
			}
		}

		listen<TickEvent.Pre> {
			val stack = selectStack {
				{ it.blockItem.let { blockItem -> blockItem != null && blockItem.block !in blacklistedBlocks } }
			}.filterStacks(HotbarContainer.stacks).firstOrNull() ?: return@listen
			val playerSupport = player.blockPos.down()
			val alreadySupported = blockState(playerSupport).hasSolidTopSurface(world, playerSupport, player)
			if (alreadySupported) return@listen
			val offset = if (descend.isSatisfied()) descendAmount else 0
			val beneath = playerSupport.down(offset)
			runSafeAutomated {
				scaffoldPositions(beneath)
					.associateWith { TargetState.State(stack.item.block.defaultState) }
					.simulate()
					.interactRequest(pendingActions)
					?.submit()
			}
		}
	}

	private fun SafeContext.scaffoldPositions(beneath: BlockPos): List<BlockPos> {
		if (!blockState(beneath).isReplaceable) return emptyList()
		if (interactConfig.airPlace.isEnabled) return listOf(beneath)

		return BlockPos.iterateOutwards(beneath, bridgeRange, bridgeRange, bridgeRange)
			.asSequence()
			.filter { !onlyBelow || it.y <= beneath.y }
			.filter { blockState(it).isReplaceable }
			.map { it.blockPos }
			.toList()
	}
}