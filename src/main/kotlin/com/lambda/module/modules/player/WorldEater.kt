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

import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color

object WorldEater : Module(
    name = "WorldEater",
    description = "Eats the world",
    tag = ModuleTag.PLAYER,
) {
    //    private val height by setting("Height", 4, 1..10, 1)
//    private val width by setting("Width", 6, 1..30, 1)
    private val pos1 by setting("Position 1", BlockPos(351, 104, 103))
    private val pos2 by setting("Position 2", BlockPos(361, 70, 113))
    private val layerSize by setting("Layer Size", 1, 1..10, 1)
    private var runningTask: Task<*>? = null
    private var area = BlockBox.create(pos1, pos2)
    private val work = mutableListOf<BlockBox>()

    init {
        onEnable {
            area = BlockBox.create(pos1, pos2)
            val layerRanges = (area.minY..area.maxY step layerSize).reversed()
            work.addAll(layerRanges.mapNotNull { y ->
                if (y == area.minY) return@mapNotNull null
                BlockBox(area.minX, y - layerSize, area.minZ, area.maxX, y, area.maxZ)
            })

            buildLayer()
        }

        onDisable {
            runningTask?.cancel()
            runningTask = null
            work.clear()
            BaritoneManager.cancel()
        }

        tickedRenderer("WorldEater Ticked Renderer") {
            box(Box.enclosing(pos1, pos2)) {
                hideFill()
                outlineColor(Color.BLUE)
            }
        }
    }

    private fun buildLayer() {
        work.firstOrNull()?.let { box ->
            runningTask = build {
                box.toStructure(TargetState.Air)
                    .toBlueprint()
            }.finally {
                work.removeFirstOrNull()
                buildLayer()
            }.run()
        } ?: disable()
    }
}
