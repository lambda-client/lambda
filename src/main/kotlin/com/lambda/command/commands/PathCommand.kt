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
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.core.Stance
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching
import net.minecraft.command.argument.BlockPredicateArgumentType
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.util.UUID

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
    usage = "path <go|plan|find|follow|cancel|clear> -- go [<x> <y> <z> ...] | plan [<x> <y> <z> ...] | find <block> | follow <block|player|entity> <value> | cancel | clear",
    description = "Walks to waypoints, or next to the nearest block of a type by path, along simulated, certified trajectories",
    examples = listOf("path go 10 100 20", "path go 10 64 20 40 70 -30", "path go ~ ~ ~20", "path plan 10 64 20", "path go", "path find chest", "path find #beds", "path follow block #pressure_plates", "path follow player Steve", "path follow entity cow", "path cancel"),
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

        required(literal("follow")) {
            execute {
                info(PathingService.findStatus)
            }
            required(literal("player")) {
                required(word("name")) { name ->
                    suggests { _, builder ->
                        val self = mc.player
                        suggestMatching(mc.world?.players?.filter { it !== self }?.map { it.gameProfile.name }.orEmpty(), builder)
                    }
                    execute {
                        val wanted = name().value()
                        PathingService.follow(wanted) { world.players.firstOrNull { it !== player && it.gameProfile.name.equals(wanted, ignoreCase = true) } }
                        info("Following $wanted...")
                    }
                }
            }
            required(literal("entity")) {
                required(word("type or uuid")) { type ->
                    suggests { _, builder ->
                        val self = mc.player
                        val entities = mc.world?.entities?.filter { it !== self && it.isAlive }.orEmpty()
                        val types = entities.map { Registries.ENTITY_TYPE.getId(it.type).path }.distinct()
                        val nearest = self?.let { player -> entities.sortedBy { it.squaredDistanceTo(player) } }.orEmpty().take(MAX_UUID_SUGGESTIONS)
                        suggestMatching(types + nearest.map { it.uuidAsString }, builder)
                    }
                    execute {
                        val id = type().value()
                        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
                        if (uuid != null) {
                            val named = mc.world?.entities?.firstOrNull { it.uuid == uuid }?.let { "${it.name.string} ($id)" } ?: id
                            PathingService.follow(named) { world.entities.firstOrNull { it.uuid == uuid && it.isAlive } }
                            info("Following $named...")
                            return@execute
                        }
                        val entityType = Identifier.tryParse(id)?.let { Registries.ENTITY_TYPE.getOptionalValue(it).orElse(null) }
                        if (entityType == null) {
                            info("Unknown entity type or uuid $id.")
                            return@execute
                        }
                        PathingService.follow("the nearest $id") {
                            world.entities.filter { it.type == entityType && it !== player && it.isAlive }.minByOrNull { it.squaredDistanceTo(player) }
                        }
                        info("Following the nearest $id...")
                    }
                }
            }
            required(literal("block")) {
                required(blockPredicate("block", gameRegistry)) { block ->
                    execute {
                        val predicate = block().value()
                        if ((predicate as? BlockPredicateArgumentType.BlockPredicate)?.hasNbt() == true) {
                            info("Block data filters ({...}) can't be checked against cached terrain; leave them out.")
                            return@execute
                        }
                        val context = block().context
                        val description = context.nodes.last().range.get(context.input)
                        PathingService.find(description, predicate, follow = true)
                        info("Following $description...")
                    }
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

    private const val MAX_UUID_SUGGESTIONS = 16

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
