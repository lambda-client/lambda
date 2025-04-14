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

import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.movement.Pathfinder
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.fastVectorOf

object PathCommand : LambdaCommand(
    name = "path",
    usage = "path <markDirty>",
    description = "Move through world"
) {
    override fun CommandBuilder.create() {
        required(literal("markDirty")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val dirty = fastVectorOf(x().value(), y().value(), z().value())
                            Pathfinder.graph.markDirty(dirty)
                            Pathfinder.graph.updateDirtyNode(dirty)
                        }
                    }
                }
            }
        }
    }
}
