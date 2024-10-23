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

package com.lambda.command.commands

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.threading.runSafe
import com.lambda.util.extension.CommandBuilder
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockBox

object BuildCommand : LambdaCommand(
    name = "Build",
    description = "Builds a structure",
    usage = "build <structure>"
) {
    override fun CommandBuilder.create() {
        required(literal("place")) {
            execute {
                runSafe {
                    val materials = setOf(
                        TargetState.Block(Blocks.NETHERRACK),
                        TargetState.Block(Blocks.AIR),
                        TargetState.Block(Blocks.COBBLESTONE),
                        TargetState.Block(Blocks.AIR),
                    )
                    val facing = player.horizontalFacing
                    val pos = player.blockPos.add(facing.vector.multiply(2))

                    BlockBox.create(pos, pos.add(facing.rotateYClockwise().vector.multiply(3)))
                        .toStructure(TargetState.Block(Blocks.NETHERRACK))
                        .toBlueprint {
                            it.mapValues { (_, _) ->
                                materials.elementAt((System.currentTimeMillis() / 5000).toInt() % materials.size)
                            }
                        }
                        .build(finishOnDone = false)
                        .start(null)
                }
            }
        }
    }
}
