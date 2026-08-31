/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.command.commands

import com.lambda.Lambda.mc
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.core.Stance
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder

/**
 * The one pathing command.
 *
 * - `path go` walks the staged goal; `path go <x> <y> <z> [<x> <y> <z> ...]`
 *   walks the given waypoints directly, in order (each leg planned, certified,
 *   and replayed before the next is submitted).
 * - `path goal <x> <y> <z> [...]` stages a goal without walking; bare
 *   `path goal` shows it, `path goal clear` forgets it.
 * - `path cancel` (or `path c`) stops the walk and drops queued waypoints.
 * - `path clear` additionally wipes the published path and telemetry.
 *
 * Coordinates accept `~` and `~n` relative to the player's stance. The command
 * owns no planning or execution: it hands the route to [PathingManager] and the
 * manager plans, certifies, and replays it. `PathingRenderer` draws the result.
 */
object PathCommand : LambdaCommand(
    name = "path",
    usage = "path <go|goal|cancel> -- go [<x> <y> <z> ...] | goal [<x> <y> <z> ...|clear] | cancel",
    description = "Walks to staged or given waypoints along simulated, certified trajectories",
    examples = listOf("path go 10 100 20", "path goal 10 64 20 40 70 -30", "path go", "path cancel"),
) {
    override fun CommandBuilder.create() {
        required(literal("go")) {
            execute {
                val pending = PathingWaypoints.pending
                if (pending.isEmpty()) {
                    info("No goal staged. `path goal <x> <y> <z> [...]` stages one, or `path go <x> <y> <z> [...]` walks directly.")
                    return@execute
                }
                PathingManager.route(AutomationConfig.DEFAULT, pending)
                info(PathingWaypoints.describe("Pathing", pending) + "...")
            }
            required(greedyString("waypoints")) { waypoints ->
                execute {
                    val parsed = parse(waypoints().value()) ?: return@execute
                    PathingManager.route(AutomationConfig.DEFAULT, parsed)
                    info(PathingWaypoints.describe("Pathing", parsed) + "...")
                }
            }
        }

        required(literal("goal")) {
            execute {
                val pending = PathingWaypoints.pending
                if (pending.isEmpty()) {
                    info("No goal staged. `path goal <x> <y> <z> [...]` stages one; `path go` walks it.")
                } else {
                    info(PathingWaypoints.describe("Staged goal:", pending))
                }
            }
            for (word in listOf("clear", "reset", "none")) {
                required(literal(word)) {
                    execute {
                        PathingWaypoints.pending = emptyList()
                        info("Goal cleared.")
                    }
                }
            }
            required(greedyString("waypoints")) { waypoints ->
                execute {
                    val parsed = parse(waypoints().value()) ?: return@execute
                    PathingWaypoints.pending = parsed
                    info(PathingWaypoints.describe("Goal staged:", parsed) + " -- `path go` walks it.")
                }
            }
        }

        for (word in listOf("cancel", "c", "stop")) {
            required(literal(word)) {
                execute {
                    PathingManager.cancel()
                    info("Pathing cancelled.")
                }
            }
        }

        required(literal("clear")) {
            execute {
                PathingManager.cancel()
                PathingManager.clear()
                info("Cleared the published path.")
            }
        }
    }

    /** Parses waypoint triples against the player's stance; reports on failure. */
    private fun parse(raw: String): List<Stance>? {
        val player = mc.player ?: return null
        val origin = Stance.of(player.pos, player.isOnGround)
        val parsed = PathingWaypoints.parse(raw, origin)
        if (parsed == null) {
            info("Expected coordinate triples (absolute, ~ or ~n): $usage")
        }
        return parsed
    }
}
