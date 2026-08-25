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
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder

/**
 * Requests a walk from [PathingManager] and reports what it decided.
 *
 * The command owns no planning or execution: it submits a [PathingRequest] and the
 * manager plans, certifies, and replays it. `PathingRenderer` draws the result.
 */
object PathCommand : LambdaCommand(
    name = "path",
    usage = "path <x> <y> <z> | path stop | path clear",
    description = "Walks to a position along a simulated, certified trajectory",
    examples = listOf("path 10 100 20", "path stop"),
) {
    override fun CommandBuilder.create() {
        required(integer("x")) { x ->
            required(integer("y")) { y ->
                required(integer("z")) { z ->
                    execute {
                        val goal = Stance(x().value(), y().value(), z().value())
                        PathingRequest(AutomationConfig.DEFAULT, goal).submit()
                        info("Pathing to (${goal.x}, ${goal.y}, ${goal.z})...")
                    }
                }
            }
        }

        required(literal("stop")) {
            execute {
                PathingManager.cancel()
                info("Pathing stopped.")
            }
        }

        required(literal("clear")) {
            execute {
                PathingManager.clear()
                info("Cleared the published path.")
            }
        }
    }
}
