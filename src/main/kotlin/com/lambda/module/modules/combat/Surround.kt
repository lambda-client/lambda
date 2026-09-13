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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.stackSelection
import com.lambda.interaction.handler.handlers.findStack
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.module.modules.combat.PlayerTrap.getTrapPositions
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.build
import com.lambda.util.item.ItemUtils.block
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.BlockPos
@Suppress("unused")
object Surround : Module(
	name = "Surround",
	description = "Surrounds your players feet with any given block",
	tag = ModuleTag.COMBAT
) {
	private val blocks by setting("Blocks", setOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN))
	private val autoCenter by setting("Auto Center", true, "Snaps the player into the center of the block upon enabling")
	private val disableOnJump by setting("Disable On Jump", true, "Automatically disables Surround when jumping or leaving the hole")
	private val antiCity by setting("Anti City", true, "Places extra diagonal blocks to prevent city mining")
	private val floor by setting("Floor", true, "Places a block under feet if standing on air")

	private var task: Task<*>? = null
	private var startY = 0.0

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

		listen<TickEvent.Pre> {
			if (disableOnJump && (mc.options.jumpKey.isPressed || player.y > startY + 0.25 || (!player.isOnGround && player.y < startY - 0.5))) {
				disable()
			}
		}

		onEnable {
			startY = player.y
			if (autoCenter) {
				val center = player.blockPos.toCenterPos()
				player.setPosition(center.x, player.y, center.z)
				connection.sendPacket(
					PlayerMoveC2SPacket.PositionAndOnGround(
						center.x, player.y, center.z, player.isOnGround, player.horizontalCollision
					)
				)
			}

			task = tickingBlueprint {
				val selection =
					stackSelection {
						predicate { stack, _ ->
							stack.item is BlockItem && blocks.contains(stack.item.block)
						}
					}

				val block = (findStack(selection, ContainerSelection.HOTBAR_AND_INVENTORY)?.item as? BlockItem)
					?.block
					?: return@tickingBlueprint emptyMap()

				val trapPositions = getTrapPositions(player).filter {
					if (floor) it.y <= player.blockPos.y else it.y == player.blockPos.y
				}.toMutableSet()

				if (antiCity) {
					val feet = player.blockPos
					trapPositions.add(feet.add(1, 0, 1))
					trapPositions.add(feet.add(1, 0, -1))
					trapPositions.add(feet.add(-1, 0, 1))
					trapPositions.add(feet.add(-1, 0, -1))
				}

				trapPositions.associateWith { TargetState.Block(block) }
			}.build(finishOnDone = false)
				.start()
		}
		onDisable {
			task?.cancel()
			task = null
			startY = 0.0
		}
	}
}