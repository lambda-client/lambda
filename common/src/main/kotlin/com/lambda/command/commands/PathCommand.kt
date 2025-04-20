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

import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.movement.Pathfinder
import com.lambda.util.Communication.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.string

object PathCommand : LambdaCommand(
    name = "pathfinder",
    usage = "path <invalidate | reset | update>",
    description = "Finds a quick path through the world",
    aliases = setOf("path")
) {
    override fun CommandBuilder.create() {
        required(literal("target")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val v = fastVectorOf(x().value(), y().value(), z().value())
                            Pathfinder.target = v
                            this@PathCommand.info("Set new target at ${v.string}")
                        }
                    }
                }
            }
        }

        required(literal("invalidate")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val v = fastVectorOf(x().value(), y().value(), z().value())
                            Pathfinder.dStar.invalidate(v)
                            this@PathCommand.info("Invalidated ${v.string}")
                        }
                    }
                }
            }
        }

        required(literal("remove")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val v = fastVectorOf(x().value(), y().value(), z().value())
                            Pathfinder.graph.remove(v)
                            this@PathCommand.info("Removed ${v.string}")
                        }
                    }
                }
            }
        }

        required(literal("update")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val u = fastVectorOf(x().value(), y().value(), z().value())
                            Pathfinder.dStar.updateVertex(u)
                            this@PathCommand.info("Updated ${u.string}")
                        }
                    }
                }
            }
        }

        required(literal("successor")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val v = fastVectorOf(x().value(), y().value(), z().value())
                            this@PathCommand.info("Successors: ${Pathfinder.graph.successors[v]?.keys?.joinToString { it.string }}")
                        }
                    }
                }
            }
        }

        required(literal("predecessors")) {
            required(integer("X", -30000000, 30000000)) { x ->
                required(integer("Y", -64, 255)) { y ->
                    required(integer("Z", -30000000, 30000000)) { z ->
                        execute {
                            val v = fastVectorOf(x().value(), y().value(), z().value())
                            this@PathCommand.info("Predecessors: ${Pathfinder.graph.predecessors[v]?.keys?.joinToString { it.string }}")
                        }
                    }
                }
            }
        }

        required(literal("setEdge")) {
            required(integer("X1", -30000000, 30000000)) { x1 ->
                required(integer("Y1", -64, 255)) { y1 ->
                    required(integer("Z1", -30000000, 30000000)) { z1 ->
                        required(integer("X2", -30000000, 30000000)) { x2 ->
                            required(integer("Y2", -64, 255)) { y2 ->
                                required(integer("Z2", -30000000, 30000000)) { z2 ->
                                    required(double("cost")) { cost ->
                                        execute {
                                            val v1 = fastVectorOf(x1().value(), y1().value(), z1().value())
                                            val v2 = fastVectorOf(x2().value(), y2().value(), z2().value())
                                            val c = cost().value()
                                            Pathfinder.dStar.updateEdge(v1, v2, c)
                                            Pathfinder.needsUpdate = true
                                            this@PathCommand.info("Updated edge ${v1.string} -> ${v2.string} to cost of $c")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        required(literal("clear")) {
            execute {
                Pathfinder.graph.clear()
                this@PathCommand.info("Cleared graph")
            }
        }

        required(literal("refresh")) {
            execute {
                Pathfinder.needsUpdate = true
                this@PathCommand.info("Marked pathfinder for refresh")
            }
        }
    }
}