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

package com.lambda.module.modules.movement

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.module.Module
import com.lambda.module.modules.client.TaskFlowModule.drawables
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.AStar
import com.lambda.pathing.AStar.findPathAStar
import com.lambda.pathing.goal.SimpleGoal
import com.lambda.util.math.setAlpha
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toVec3d
import java.awt.Color

object Pathfinder : Module(
    name = "Pathfinder",
    description = "Get from A to B",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    init {
        listen<RenderEvent.StaticESP> {
            val path = findPathAStar(player.blockPos.toFastVec(), SimpleGoal(fastVectorOf(0, 120, 0)))

            path.nodes.zipWithNext { current, next ->
                val currentPos = current.pos.toBlockPos().toCenterPos()
                val nextPos = next.pos.toBlockPos().toCenterPos()
                it.renderer.buildLine(currentPos, nextPos, Color.BLUE.setAlpha(0.25))
            }
        }
    }
}