/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.MotionTemplate
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.movement.providers.ClimbMovement
import com.lambda.pathing.movement.providers.DropMovement
import com.lambda.pathing.movement.providers.JumpMovement
import com.lambda.pathing.movement.providers.SlimeBounceMovement
import com.lambda.pathing.movement.providers.WalkMovement

/**
 * The registered movements, and the templates they contribute.
 *
 * This replaced a single hardcoded `buildList` in the move library, which is the reason
 * every new kind of motion used to be a change to shared code. Registration order fixes
 * template numbering, so it is stable rather than incidental: a plan dump recorded against
 * one catalogue replays against the same one.
 */
class MovementCatalog private constructor(
    val movements: List<Movement>,
    val templates: List<MotionTemplate>,
    private val byId: Map<MovementId, Movement>,
) {
    /**
     * The movement that owns [id].
     *
     * Keyed by every id a movement *contributes*, not just its own. One movement can span
     * several -- walking owns the stride, the one-block rise and the one-block step down,
     * because they share conditions and a control program and differ only in how the route
     * renders. Indexing by the owner's id alone left those edges belonging to nothing, and
     * a route containing one was rejected as unsupported.
     *
     * Nullable rather than throwing: a plan dump or a saved route can outlive the
     * catalogue it was recorded against.
     */
    operator fun get(id: MovementId): Movement? = byId[id]

    fun supports(id: MovementId): Boolean = id in byId

    companion object {
        /**
         * Everything that ships.
         *
         * Adding a movement is adding a line here plus the file it names. Nothing else in
         * the graph, the search, the renderer or the dump has to learn about it.
         */
        val REGISTERED: List<Movement> = listOf(
            WalkMovement,
            JumpMovement,
            DropMovement,
            ClimbMovement,
            SlimeBounceMovement,
        )

        fun build(
            costs: CoarseMoveCosts,
            options: SimpleMoveOptions = SimpleMoveOptions(),
            ballistics: BallisticProfile = BallisticProfile.VANILLA,
            movements: List<Movement> = REGISTERED,
        ): MovementCatalog {
            val context = MovementContext(options, costs, ballistics)
            val owners = LinkedHashMap<MovementId, Movement>()
            val specs = ArrayList<TemplateSpec>()
            // A movement that contributes no edges is not registered at all, rather than
            // registered and empty. Otherwise a switched-off movement still answers
            // `occupies`, and the graph fills with cells it considers reachable and has no
            // way to leave -- a ladder column that swallows the search with climbing off.
            val active = movements.filter { movement ->
                val contributed = movement.templates(context)
                if (contributed.isEmpty()) return@filter false
                owners[movement.id] = movement
                contributed.forEach { owners.putIfAbsent(it.movement, movement) }
                specs += contributed
                true
            }
            val templates = specs.mapIndexed { index, spec -> spec.toTemplate(MotionTemplateId(index)) }
            return MovementCatalog(active, templates, owners)
        }
    }
}
