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

package com.lambda.module.modules.player

import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BaritoneUtils
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import net.minecraft.util.math.BlockPos

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val height by setting("Height", 4, 1..8, 1)
    private val width by setting("Width", 4, 1..8, 1)
    private val flatten by setting("Flatten", true)
    private val fillFluids by setting("Fill Fluids", false, "Removes liquids by filling them in before breaking")
    private val instantOnly by setting("Instant Only", false)
    private val fillFloor by setting("Fill Floor", false)
    private val baritoneSelection by setting("Baritone Selection", false, "Restricts nuker to your baritone selection")

    private var task: Task<*>? = null

    init {
        onEnable {
            task = tickingBlueprint {
                val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
                    .asSequence()
                    .map { it.blockPos }
                    .filter { !blockState(it).isAir }
                    .filter { !flatten || it.y >= player.blockPos.y }
                    .filter { !instantOnly || blockState(it).getHardness(world, it) <= TaskFlowModule.build.breaking.breakThreshold }
                    .filter { pos ->
                        if (!baritoneSelection) true
                        else BaritoneUtils.primary.selectionManager.selections.any {
                            val min = it.min()
                            val max = it.max()
                            pos.x >= min.x && pos.x <= max.x
                                    && pos.y >= min.y && pos.y <= max.y
                                    && pos.z >= min.z && pos.z <= max.z
                        }
                    }
                    .associateWith { if (fillFluids) TargetState.Air else TargetState.Empty }

                if (fillFloor) {
                    val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
                        .map { it.blockPos }
                        .associateWith { TargetState.Solid }
                    return@tickingBlueprint selection + floor
                }

                selection
            }.build(finishOnDone = false)
            // ToDo: Add build setting delegates

            task?.run()
        }

        onDisable {
            task?.cancel()
        }
    }
}
