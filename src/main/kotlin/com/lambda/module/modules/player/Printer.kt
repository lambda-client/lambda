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

import com.lambda.config.AutomationConfig.Companion.automationConfig
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.placing.PlaceConfig
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.minecraft.util.math.BlockPos

object Printer : Module(
    name = "Printer",
    description = "Automatically prints schematics",
    tag = ModuleTag.PLAYER
) {
    private fun isSchematicHandlerAvailable(): Boolean = runCatching {
        Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler")
        true
    }.getOrDefault(false)

    private val range by setting("Range", 5, 1..7, 1)
    private val air by setting("Air", false)

    private var buildTask: Task<*>? = null

    init {
        defaultAutomationConfig = automationConfig {
            buildConfig.apply {
                editTyped(::pathing, ::stayInRange) { defaultValue(false) }
            }
            breakConfig.apply {
                editTyped(::efficientOnly, ::suitableToolsOnly) { defaultValue(false) }
            }
            placeConfig.apply {
                ::airPlace.edit { defaultValue(PlaceConfig.AirPlaceMode.Grim) }
            }
        }
        onEnable {
            if (!isSchematicHandlerAvailable()) {
                error("Litematica is not installed!")
                disable()
                return@onEnable
            }
            buildTask = TickingBlueprint {
                val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return@TickingBlueprint emptyMap()
                BlockPos.iterateOutwards(player.blockPos, range, range, range)
                    .asSequence()
                    .map { it.blockPos }
                    .associateWith { TargetState.State(schematicWorld.getBlockState(it)) }
                    .filter { air || !it.value.blockState.isAir }
            }.build(finishOnDone = false).run()
        }

        onDisable { buildTask?.cancel(); buildTask = null }
    }
}