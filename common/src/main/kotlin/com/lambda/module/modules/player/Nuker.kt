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

import com.lambda.interaction.construction.Blueprint.Companion.emptyStructure
import com.lambda.interaction.construction.DynamicBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.BuildTask.Companion.build
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
    private val onlyBreakInstant by setting("Only Break Instant", true)
    private val fillFloor by setting("Fill Floor", false)

    private var task = emptyTask()

    init {
        onEnable {
            task = emptyStructure()
                .toBlueprint {
                    val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
                        .asSequence()
                        .map { it.blockPos }
                        .filter { !world.isAir(it) }
                        .filter { !flatten || it.y >= player.blockPos.y }
                        .filter { !onlyBreakInstant || it.blockState(world).getHardness(world, it) <= 1 }
                        .filter { it.blockState(world).getHardness(world, it) >= 0 }
                        .associateWith { TargetState.Air }

                    if (fillFloor) {
                        val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
                            .map { it.blockPos }
                            .associateWith { TargetState.Solid }
                        return@toBlueprint selection + floor
                    }

                    selection
                }
                .build(
                    pathing = false,
                    finishOnDone = false,
                    cancelOnUnsolvable = false
                )
            task.start(null)
        }

        onDisable {
            task.cancel()
        }

//        listener<TickEvent.Pre> {
//            task?.let {
//                if (!it.isRunning) return@listener
//
//                info(it.info)
//            }
//        }
    }
}
