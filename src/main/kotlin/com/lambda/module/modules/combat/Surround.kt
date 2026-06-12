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

import com.lambda.config.ConfigEditor.editTypedSettings
import com.lambda.config.ConfigEditor.hideBlock
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.withEdits
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.modules.combat.PlayerTrap.getTrapPositions
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem

@Suppress("unused")
object Surround : Module(
	name = "Surround",
	description = "Surrounds your players feet with any given block",
	tag = ModuleTag.COMBAT
) {
	private val blocks by setting("Blocks", setOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN))

	private var task: Task<*>? = null

	init {
		setDefaultAutomationConfig()
			.withEdits {
				buildConfig.apply {
					editTypedSettings(
						::pathing,
						::stayInRange,
						::spleefEntities,
						::collectDrops
					) { defaultValue(false); hide() }
				}
				hideBlock(::eatConfig)
			}

		onEnable {
			task = tickingBlueprint {
				val block = player.hotbarAndInventoryStacks.firstOrNull {
					it.item is BlockItem && blocks.contains(it.item.block)
				}?.item?.block ?: return@tickingBlueprint emptyMap()
				getTrapPositions(player)
					.filter { it.y <= player.blockPos.y }
					.associateWith { TargetState.Block(block) }
			}.build(finishOnDone = false).run()
		}
		onDisable { task?.cancel(); task = null }
	}
}