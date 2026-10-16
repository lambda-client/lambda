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
import com.lambda.brigadier.argument.blockPredicate
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.core.Stance
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.argument.BlockPredicateArgumentType

/**
 * The one pathing command.
 *
 * - `path go <x> <y> <z> [<x> <y> <z> ...]` walks the waypoints in order. Intermediate waypoints are
 *   passed at speed as one continuous tape (Pathing setting "Waypoint Arrival"); the last rests. A bare
 *   `path go` walks the last waypoints again, or the plan `path plan` rehearsed for them.
 * - `path plan [<x> <y> <z> ...]` rehearses the waypoints (or the last ones) without moving: the search
 *   walks them in simulation and keeps refining the shortest tape; `path go` then walks it from the first frame.
 * - `path find <block>` walks next to the nearest block by path matching a block id, `#tag` or
 *   `[state]` filter: it collects the closest matches from loaded chunks, Lambda's terrain cache
 *   and Baritone's cache, compares them with one coarse search toward all of them, and walks to
 *   the one the cheapest route ends at; a remembered block is checked again once its chunk loads,
 *   and an unreachable one gives way to the next nearest. Bare `path find` shows the search.
 * - `path cancel` (or `path c`, `path stop`) stops the walk; `path clear` also wipes the published path and telemetry.
 *
 * Coordinates accept `~` and `~n` relative to the player's stance. The command
 * owns no planning or execution: it hands the route to [PathingService] and the
 * pathing session plans, certifies, and replays it. `PathingRenderer` draws the result.
 */
object PathCommand : LambdaCommand(
    name = "path",
    usage = "path <go|plan|find|cancel|clear> -- go [<x> <y> <z> ...] | plan [<x> <y> <z> ...] | find <block> | cancel | clear",
    description = "Walks to waypoints, or next to the nearest block of a type by path, along simulated, certified trajectories",
    examples = listOf("path go 10 100 20", "path go 10 64 20 40 70 -30", "path go ~ ~ ~20", "path plan 10 64 20", "path go", "path find chest", "path find #beds", "path cancel"),
) {
    override fun CommandBuilder.create() {
        required(literal("go")) {
            execute {
                val last = PathingWaypoints.last
                if (last.isEmpty()) {
                    info("Nothing to walk yet. `path go <x> <y> <z> [...]` walks waypoints; `path plan <x> <y> <z> [...]` rehearses them first.")
                    return@execute
                }
                walk(last)
            }
            required(greedyString("waypoints")) { waypoints ->
                execute {
                    val parsed = parse(waypoints().value()) ?: return@execute
                    walk(parsed)
                }
            }
        }

        required(literal("plan")) {
            execute {
                val last = PathingWaypoints.last
                if (last.isEmpty()) {
                    info("Nothing to plan yet. `path plan <x> <y> <z> [...]` rehearses waypoints without moving.")
                    return@execute
                }
                rehearse(last)
            }
            required(greedyString("waypoints")) { waypoints ->
                execute {
                    val parsed = parse(waypoints().value()) ?: return@execute
                    rehearse(parsed)
                }
            }
        }

        required(literal("find")) {
            execute {
                info(PathingService.findStatus)
            }
            required(blockPredicate("block", gameRegistry)) { block ->
                execute {
                    val predicate = block().value()
                    if ((predicate as? BlockPredicateArgumentType.BlockPredicate)?.hasNbt() == true) {
                        info("Block data filters ({...}) can't be checked against cached terrain; leave them out.")
                        return@execute
                    }
                    val context = block().context
                    val description = context.nodes.last().range.get(context.input)
                    PathingService.find(description, predicate)
                    info("Searching for $description...")
                }
            }
        }

        for (word in listOf("cancel", "c", "stop")) {
            required(literal(word)) {
                execute {
                    PathingService.cancel()
                    info("Pathing cancelled.")
                }
            }
        }

        required(literal("clear")) {
            execute {
                PathingService.cancel()
                PathingService.clear()
                info("Cleared the published path.")
            }
        }
    }

    private fun walk(waypoints: List<Stance>) {
        PathingWaypoints.last = waypoints
        PathingService.route(AutomationConfig.DEFAULT, waypoints)
        info(PathingWaypoints.describe("Pathing", waypoints) + "...")
    }

    private fun rehearse(waypoints: List<Stance>) {
        PathingWaypoints.last = waypoints
        PathingService.rehearse(AutomationConfig.DEFAULT, waypoints)
        info(PathingWaypoints.describe("Rehearsing", waypoints) + " -- `path go` walks the best plan so far.")
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
