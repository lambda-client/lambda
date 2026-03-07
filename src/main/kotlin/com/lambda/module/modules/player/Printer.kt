/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.Communication.logError
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.minecraft.util.math.BlockPos

object Printer : Module(
	name = "Printer",
	description = "Automatically prints schematics",
	tag = ModuleTag.PLAYER
) {
	private val range by setting("Range", 5, 1..7, 1)
	private val considerAll by setting("Consider All", false, description = "Consider all blocks outside the schematic as schematic air blocks")

	private var buildTask: Task<*>? = null

	init {
		setDefaultAutomationConfig()

		onEnable {
			if (!litematicaAvailable()) {
				logError("Litematica is not installed!")
				disable()
				return@onEnable
			}
			buildTask = TickingBlueprint {
				val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return@TickingBlueprint emptyMap()
				BlockPos.iterateOutwards(player.blockPos, range, range, range)
					.map { it.blockPos }
					.asSequence()
					.filter { DataManager.getRenderLayerRange().isPositionWithinRange(it) }
					.associateWith { TargetState.State(schematicWorld.getBlockState(it)) }
					.filter { considerAll || !it.value.blockState.isAir }
			}.build(finishOnDone = false).run()
		}

		onDisable { buildTask?.cancel(); buildTask = null }
	}

	private fun litematicaAvailable(): Boolean = runCatching {
		Class.forName("fi.dy.masa.litematica.Litematica")
		true
	}.getOrDefault(false)
}