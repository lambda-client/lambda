/*
 * Copyright 2024 Lambda
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
import com.lambda.config.groups.RotationSettings
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.allSigns
import com.lambda.util.item.ItemUtils
import net.minecraft.state.property.Properties

object TaskFlow : Module(
    name = "TaskFlow",
    description = "Settings for task automation",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.AUTOMATION)
) {
    enum class Page {
        BUILD, ROTATION, INTERACTION, TASKS
    }

    private val page by setting("Page", Page.BUILD)
    val build = BuildSettings(this) {
        page == Page.BUILD
    }
    val rotation = RotationSettings(this) {
        page == Page.ROTATION
    }
    val interact = InteractionSettings(this) {
        page == Page.INTERACTION
    }
    val taskCooldown by setting("Task Cooldown", 0, 0..10000, 10, unit = " ms") {
        page == Page.TASKS
    }
    val disposables by setting("Disposables", ItemUtils.defaultDisposables)
    val ignoredBlocks by setting("Ignored Blocks", allSigns)
    val defaultIgnoreTags = setOf(
        Properties.DISTANCE_1_7,
        Properties.PERSISTENT,
        Properties.WATERLOGGED,
        Properties.STAIR_SHAPE,
        Properties.UP,
        Properties.DOWN,
        Properties.NORTH,
        Properties.EAST,
        Properties.SOUTH,
        Properties.WEST
    )
//    val ignoredTags by setting("Ignored Tags", defaultIgnoreTags)
}
