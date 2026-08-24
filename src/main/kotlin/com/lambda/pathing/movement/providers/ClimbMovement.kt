/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement.providers

import com.lambda.pathing.movement.*
import com.lambda.pathing.world.Medium

/**
 * Ladders and vines.
 *
 * This exists as much to prove the seam as to climb anything. It is a movement in a medium
 * the walking planner cannot describe, and adding it touched no other movement: a new
 * [Medium], a [CellPredicate] that tests for it, templates whose delta is purely vertical,
 * its own program, and a [completed] rule that is not "grounded and the stance changed" --
 * a climber's stance changes without either.
 *
 * Vanilla climbing is a velocity clamp rather than a gait: hold into the block and the body
 * rises at 0.117 blocks per tick, release and it descends at a capped 0.15. There is no
 * acceleration to solve, which is why this needs no launch arithmetic.
 */
object ClimbMovement : Movement {
    override val id = MovementId.CLIMB

    override fun templates(context: MovementContext): List<TemplateSpec> {
        if (!context.options.allowClimbing) return emptyList()
        return buildList {
            // Up and down the column, so long as the body is holding a climbable and the
            // cell it moves into is one too.
            add(
                TemplateSpec(
                    dx = 0, dy = 1, dz = 0,
                    movement = id,
                    cost = context.costs.climbCost(1),
                    conditions = listOf(
                        CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, 1, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, 2, 0, CellPredicate.CENTER_HEAD),
                    ),
                )
            )
            add(
                TemplateSpec(
                    dx = 0, dy = -1, dz = 0,
                    movement = id,
                    cost = context.costs.climbCost(1),
                    conditions = listOf(
                        CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, -1, 0, CellPredicate.CLIMBABLE),
                    ),
                )
            )

            // Over the lip into the top of a shaft.
            //
            // A ladder whose column starts below the deck it serves -- which is how most
            // are built -- has no horizontal way in from that deck. Climbing out of one
            // always worked, because a walking step-up carries the body off the top rung;
            // there was simply no mirror of it going down, so a route stopped dead at the
            // edge and the body milled about on the lip looking for a way in.
            for ((dx, dz) in WalkMovement.CARDINALS) {
                add(
                    TemplateSpec(
                        dx = dx, dy = -1, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(dx, -1, dz, CellPredicate.CLIMBABLE),
                            // The body standing on the rung fills this cell too.
                            CellCondition(dx, 0, dz, CellPredicate.CENTER_HEAD),
                        ),
                    )
                )
            }

            // Stepping onto the ladder from the ground beside it, and off it onto a ledge.
            for ((dx, dz) in WalkMovement.CARDINALS) {
                add(
                    TemplateSpec(
                        dx = dx, dy = 0, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(dx, 0, dz, CellPredicate.CLIMBABLE),
                            CellCondition(dx, 1, dz, CellPredicate.CENTER_HEAD),
                        ),
                    )
                )
                add(
                    TemplateSpec(
                        dx = dx, dy = 0, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        ) + WalkMovement.stanceConditions(dx, 0, dz),
                    )
                )
            }
        }
    }

    /** A body holding a climbable is somewhere, even with nothing under its feet. */
    override fun occupies(view: com.lambda.pathing.world.CoarseVoxelView, stance: com.lambda.pathing.coarse.Stance): Boolean =
        view.medium(stance.x, stance.y, stance.z) == Medium.CLIMBABLE

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val edge = context.edge
        val vertical = edge.from.x == edge.to.x && edge.from.z == edge.to.z

        if (!vertical) {
            // Stepping onto the ladder, off it onto a ledge, or over a lip into the top of
            // it. Here the direction of travel *is* the answer, and the key has to be held
            // to get the body moving at all.
            //
            // What differs is what happens once it is off the ground. Stepping *off* a
            // column has to keep pressing or it never crosses to the ledge. Stepping *into*
            // one over a lip must stop: the body is already where it wants to be, and a
            // press there is a horizontal collision, which vanilla answers by re-asserting
            // the climb rise and carrying it straight back up.
            val enteringOverLip = edge.to.y < edge.from.y
            val yaw = bearingBetween(edge.from.center(), edge.to.center())
            return listOf(
                TrajectoryDecision.Climb(
                    step = edge.to,
                    holdForward = true,
                    holdWhileClimbing = !enteringOverLip,
                    climbWithJump = false,
                    yaw = yaw,
                )
            )
        }

        // Up or down the column, where the delta is purely vertical and the bearing
        // between the two centres is a zero vector. Asking it anyway is what broke this:
        // `atan2(0, 0)` is 0, so every climb in the world was flown facing due east, and
        // a body that holds forward facing away from its ladder walks straight off it.
        //
        // The direction that matters is the one the climbable is hung on, which is a fact
        // about the terrain rather than about the move.
        val hold = supportBearing(context.view, edge.from, edge.to)
        val ascending = edge.to.y > edge.from.y
        return listOf(
            TrajectoryDecision.Climb(
                step = edge.to,
                // Pressing into the hold is what re-asserts the rise, so it is held for an
                // ascent and only when there is something to press. Descending must never
                // press: a horizontal collision would send the body back up. And with no
                // hold at all -- a vine under an overhang -- pressing would walk the body
                // out of the hitbox that is keeping it up.
                holdForward = ascending && hold != null,
                holdWhileClimbing = ascending && hold != null,
                // The jump key rises without contact, which is the only way up a climbable
                // with nothing behind it, and does no harm alongside a press.
                climbWithJump = ascending,
                // Faced into the wall even on the way down, so a body that drifts is
                // steered back onto its hold rather than out of the column. With no hold
                // there is nothing to face, and turning for its own sake only costs.
                yaw = hold,
            )
        )
    }

    /**
     * Which way the climbable is hung, as a yaw pointing into its support.
     *
     * The direction has to be solid at *both* ends of the move, and that is the whole
     * subtlety. A ladder column often adjoins more than one solid face -- the deck it
     * arrives at, the floor it stands on -- and taking whichever was found first aimed the
     * body at a wall that was not its ladder. On a descent past a deck that meant facing
     * the deck, being shoved off the column, and riding back down the way it came.
     *
     * A mount that only exists at one end cannot hold the body through the move, so
     * requiring both is not a tie-break: it is the actual condition. The wall a ladder
     * hangs on satisfies it at every rung; anything incidental beside the column does not.
     *
     * Null where nothing qualifies -- vines under an overhang, a wall that has been mined
     * out. There is no contact to press into there, so the caller falls back rather than
     * inventing one.
     */
    private fun supportBearing(
        view: com.lambda.pathing.world.CoarseVoxelView,
        from: com.lambda.pathing.coarse.Stance,
        to: com.lambda.pathing.coarse.Stance,
    ): Double? {
        val support = WalkMovement.CARDINALS.firstOrNull { (dx, dz) ->
            view.medium(from.x + dx, from.y, from.z + dz) == Medium.SOLID &&
                view.medium(to.x + dx, to.y, to.z + dz) == Medium.SOLID
        } ?: return null
        val (dx, dz) = support
        return bearingBetween(from.center(), from.offset(dx, 0, dz).center())
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision as TrajectoryDecision.Climb
        return ClimbProgram(
            targetYaw = decision.yaw,
            holdForward = decision.holdForward,
            holdWhileClimbing = decision.holdWhileClimbing,
            climbWithJump = decision.climbWithJump,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
        )
    }

    /**
     * A climb is over when the body reaches the next cell of the column.
     *
     * Note what is absent: no grounded test, and no requirement that the horizontal stance
     * changed. Both are built into the search's default rule and both are wrong here -- a
     * climbing body is not on the ground and does not move horizontally at all.
     */
    @Suppress("ReturnCount")
    override fun completed(context: CompletionContext): Boolean {
        val target = (context.decision as? TrajectoryDecision.Climb)?.step
            ?: return context.stance != context.body.stance
        // Arrival is reaching the cell that was aimed at, not merely leaving the one the
        // body started in. Stepping over a lip into the top of a shaft crosses into the
        // column while the feet are still on the deck above, and calling that arrival
        // anchors the body one level up from the rung it was going for -- on a stance that
        // does not exist, which the search then throws away.
        return context.stance == target
    }

    /**
     * A climb finishes in the air, because a climbing body is never on the ground.
     *
     * [completed] above says as much, but saying it was not enough: the search only asked
     * the question on grounded frames, so a climb's own rule was never consulted and no
     * transition up a ladder could ever finish. The route existed and the body never flew
     * it.
     */
    override val completesAirborne: Boolean get() = true

    /** The wall is the hold. Vanilla only re-asserts the rise while the body is against it. */
    override val pressesIntoTerrain: Boolean get() = true

}
