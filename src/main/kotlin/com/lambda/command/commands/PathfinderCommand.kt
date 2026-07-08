/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.command.commands

import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.pathing.goal.TraversalGoal
import com.lambda.pathing.manager.PathfinderManager
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos

object PathfinderCommand : LambdaCommand(
    name = "pathfinder",
    aliases = setOf("path"),
    usage = "pathfinder <go|refresh|cancel|status>",
    description = "Debug commands for Lambda's Lazy D* Lite pathfinder",
) {
    override fun CommandBuilder.create() {
        required(literal("go")) {
            required(integer("x", -30_000_000, 30_000_000)) { x ->
                required(integer("y", -2048, 2048)) { y ->
                    required(integer("z", -30_000_000, 30_000_000)) { z ->
                        execute {
                            val target = fastVectorOf(x().value(), y().value(), z().value())
                            val handle = with(PathfinderManager) {
                                runSafeAutomated {
                                    requestTraversal(
                                        goal = TraversalGoal.Block(target),
                                        owner = this@PathfinderCommand,
                                    )
                                }
                            }

                            if (handle == null) {
                                this@PathfinderCommand.info("Cannot request traversal outside of a world")
                            } else {
                                this@PathfinderCommand.info("Requested path to ${target.toBlockPos().toShortString()}: ${handle.status} (${handle.path.size} nodes)")
                            }
                        }
                    }
                }
            }
        }

        required(literal("refresh")) {
            execute {
                val handle = with(PathfinderManager) {
                    runSafeAutomated { refreshActiveTraversal() }
                }
                if (handle == null) this@PathfinderCommand.info("No active traversal to refresh")
                else this@PathfinderCommand.info("Refreshed traversal #${handle.id}: ${handle.status} (${handle.path.size} nodes)")
            }
        }

        required(literal("cancel")) {
            execute {
                if (PathfinderManager.cancelActiveTraversal()) this@PathfinderCommand.info("Cancelled active traversal")
                else this@PathfinderCommand.info("No active traversal to cancel")
            }
        }

        required(literal("status")) {
            execute {
                this@PathfinderCommand.info(PathfinderManager.debugInfo())
            }
        }
    }
}
