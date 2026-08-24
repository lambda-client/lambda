/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement.providers

import com.lambda.pathing.movement.TemplateSpec
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.CellPredicate
import com.lambda.pathing.movement.CompletionContext
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.HeadingFollowerProgram
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.movement.MovementContext
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.ProgramContext
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.movement.center

/**
 * Ordinary ground travel: a stride, a diagonal, a one-block rise, a one-block step down.
 *
 * These four share a body of conditions and a single control program, which is why they
 * are one movement rather than four. What separates them from everything else in the
 * catalogue is that the feet never deliberately leave the ground -- the body walks off a
 * one-block step because gravity takes it, not because anything was aimed.
 */
object WalkMovement : Movement {
    override val id = MovementId.WALK

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        val costs = context.costs
        for ((dx, dz) in CARDINALS) {
            add(spec(dx, 0, dz, costs.cardinalWalk, stanceConditions(dx, 0, dz)))

            if (options.allowStepUp) {
                add(
                    spec(
                        dx, 1, dz, costs.stepUp,
                        stanceConditions(dx, 1, dz) + CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE),
                        MovementId.STEP_UP,
                    )
                )
            }

            if (options.maxWalkOffDepth >= 1) {
                add(
                    spec(
                        dx, -1, dz, costs.walkOffCost(1),
                        stanceConditions(dx, -1, dz) +
                            (1 downTo 0).map { y -> CellCondition(dx, y, dz, CellPredicate.CENTER_SLICE) },
                        MovementId.WALK_OFF,
                    )
                )
            }
        }

        if (options.allowDiagonal) {
            for ((dx, dz) in DIAGONALS) {
                // Both flanking columns have to be clear, not just the destination: a
                // diagonal stride clips the corner between them.
                add(
                    spec(
                        dx, 0, dz, costs.diagonalWalk,
                        stanceConditions(dx, 0, dz) + listOf(
                            CellCondition(dx, 0, 0, CellPredicate.FULL_SLICE),
                            CellCondition(dx, 1, 0, CellPredicate.FULL_HEAD),
                            CellCondition(0, 0, dz, CellPredicate.FULL_SLICE),
                            CellCondition(0, 1, dz, CellPredicate.FULL_HEAD),
                        ),
                    )
                )
            }
        }
    }

    private fun spec(
        dx: Int,
        dy: Int,
        dz: Int,
        cost: Double,
        conditions: List<CellCondition>,
        movement: MovementId = id,
    ) = TemplateSpec(dx, dy, dz, movement, cost, conditions)

    /**
     * Gait and steering styles for one candidate step.
     *
     * The look-ahead and turn-easing variants exist because a corner is not a straight:
     * aiming one node ahead cuts it, aiming two rounds it, and easing off the stick through
     * a hard turn keeps the body from scrubbing into the outer wall.
     */
    override fun decisions(context: DecisionContext): List<TrajectoryDecision> = buildList {
        for (sprint in context.constraints.sprintModes) {
            for ((lookAhead, ease) in WALK_STYLES) {
                add(TrajectoryDecision.Walk(sprint, context.edge.to, lookAhead, ease))
            }
        }
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision
        if (decision is TrajectoryDecision.Heading) {
            return HeadingFollowerProgram(
                targetYaw = decision.yaw,
                sprint = decision.sprint && decision.keys.sustainsSprint,
                maxYawChange = context.constraints.maxYawDegreesPerFrame,
                launch = context.launch,
                keys = decision.keys,
                airborneKeys = decision.airborneKeys,
            )
        }
        return SegmentFollowerProgram(
            nodes = context.nodes,
            sprint = decision.sprint,
            lookAheadNodes = (decision as? TrajectoryDecision.Walk)?.lookAheadNodes ?: DEFAULT_LOOK_AHEAD,
            launch = context.launch,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
            easeTurns = (decision as? TrajectoryDecision.Walk)?.easeTurns == true,
        )
    }

    /**
     * A walk is over when the body stands somewhere new.
     *
     * A heading decision instead runs for a fixed commitment, because its whole purpose is
     * to hold a bearing through terrain the stance grid cannot describe; ending it at the
     * first stance change would abandon the turn half-made.
     */
    override fun completed(context: CompletionContext): Boolean {
        val decision = context.decision
        if (context.launch != null) return context.launch.hasFired && context.airborne
        if (decision is TrajectoryDecision.Heading) {
            return context.frameIndex + 1 >= context.headingCommitFrames
        }
        return context.stance != context.body.stance
    }

    /** Cell tests shared by everything that walks, as offsets from the destination. */
    fun stanceConditions(dx: Int, dy: Int, dz: Int) = listOf(
        CellCondition(dx, dy - 1, dz, CellPredicate.SUPPORT),
        CellCondition(dx, dy, dz, CellPredicate.CENTER_SLICE),
        CellCondition(dx, dy + 1, dz, CellPredicate.CENTER_HEAD),
    )

    val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    private const val DEFAULT_LOOK_AHEAD = 1
}
