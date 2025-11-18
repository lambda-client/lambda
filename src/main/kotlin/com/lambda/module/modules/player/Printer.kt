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

import com.lambda.config.groups.BreakSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.HotbarSettings
import com.lambda.config.groups.InteractSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.PlaceSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.placing.PlaceConfig
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.NamedEnum
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

    private val range by setting("Range", 5, 1..7, 1).group(Group.General)
    private val air by setting("Air", false).group(Group.General)

    override val buildConfig = BuildSettings(this, Group.Build).apply {
        editTyped(::pathing, ::stayInRange) { defaultValue(false) }
    }
    override val breakConfig = BreakSettings(this, Group.Break).apply {
        editTyped(::efficientOnly, ::suitableToolsOnly) { defaultValue(false) }
    }
    override val placeConfig = PlaceSettings(this, Group.Place).apply {
        ::airPlace.edit { defaultValue(PlaceConfig.AirPlaceMode.Grim) }
    }
    override val interactConfig = InteractSettings(this, Group.Interact)
    override val rotationConfig = RotationSettings(this, Group.Rotation)
    override val inventoryConfig = InventorySettings(this, Group.Inventory)
    override val hotbarConfig = HotbarSettings(this, Group.Hotbar)

    private var buildTask: Task<*>? = null

    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Build("Build"),
        Break("Break"),
        Place("Place"),
        Interact("Interact"),
        Rotation("Rotation"),
        Inventory("Inventory"),
        Hotbar("Hotbar")
    }

    init {
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