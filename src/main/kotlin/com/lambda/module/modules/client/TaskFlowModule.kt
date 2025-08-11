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

package com.lambda.module.modules.client

import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.result.Drawable
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.world.raycast.InteractionMask

object TaskFlowModule : Module(
    name = "TaskFlow",
    description = "Settings for task automation",
    tag = ModuleTag.CLIENT,
) {
    enum class Group(override val displayName: String): NamedEnum {
        Build("Build"),
        Rotation("Rotation"),
        Interaction("Interaction"),
        Inventory("Inventory"),
        Debug("Debug")
    }

    val build = BuildSettings(this, Group.Build)
    val rotation = RotationSettings(this, Group.Rotation)
    val interact = InteractionSettings(this, Group.Interaction, InteractionMask.Both)
    val inventory = InventorySettings(this, Group.Inventory)

    val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree").group(Group.Debug)
    val shrinkFactor by setting("Shrink Factor", 0.001, 0.0..1.0, 0.001).group(Group.Debug)

    @Volatile
    var drawables = listOf<Drawable>()

    init {
        listen<RenderEvent.StaticESP> {
            drawables.toList().forEach { res ->
                with(res) { buildRenderer() }
            }
        }
    }
}
