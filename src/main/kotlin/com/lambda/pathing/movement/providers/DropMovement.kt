/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement.providers

import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.MotionTemplate
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.*

/**
 * Descending a ledge under control.
 *
 * The primitive the planner used to lack entirely. Without it a descent is a walk that
 * runs out of block, carrying whatever speed the approach left -- and a sprint clears 1.26
 * blocks off a one-block ledge, so it sails past any landing narrower than that. The
 * alternative the search reached for instead was a *jump* off the ledge, which is worse
 * again: it adds height, air time and fall distance to a move whose entire purpose is to
 * lose height cheaply.
 *
 * A drop is therefore defined by what it does not do. No jump key, and a leave speed that
 * was solved rather than inherited.
 */
object DropMovement : Movement {
    override val id = MovementId.DROP

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        for ((dx, dz) in WalkMovement.CARDINALS) {
            // Depth one directly across stays a walk-off: gravity does it, and no input
            // makes it better. Everything deeper, or across a gap, has to be aimed.
            for (depth in 2..options.maxWalkOffDepth) {
                add(spec(dx, dz, span = 1, depth = depth, cost = context.costs.dropCost(1, depth)))
            }
            for (span in 2..options.maxDropSpan) {
                for (depth in 1..options.maxWalkOffDepth) {
                    add(spec(dx, dz, span, depth, context.costs.dropCost(span, depth)))
                }
            }
        }
    }

    private fun spec(dx: Int, dz: Int, span: Int, depth: Int, cost: Double) = TemplateSpec(
        dx = span * dx, dy = -depth, dz = span * dz,
        movement = id,
        cost = cost,
        conditions = WalkMovement.stanceConditions(span * dx, -depth, span * dz),
        // Restricting the arc to the non-jumping modes is the whole point: the same
        // geometry offered to a jump would be answered with a leap.
        arc = MotionTemplate.ArcSpec(dx, dz, span, -depth, MODES),
    )

    /**
     * Every descent gets a controlled option, including ones the graph called a walk-off.
     *
     * A one-block step down is priced as a walk because gravity does it, and on open ground
     * that is right. On a staircase of single blocks it is not: sprinting off covers 1.26
     * blocks, which clears the step below and lands on the one after -- or on nothing. The
     * walk stays first because it is cheaper; this is what the search falls back to.
     */
    override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y < edge.from.y

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val ideal = context.edge.launch
        val asIs = LaunchSolver.best(
            context.edge.from, context.edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { context.body.speed },
            preferredEntrySpeed = { context.body.speed },
        )
        return listOfNotNull(ideal, asIs)
            .filter { context.constraints.sprintModes.contains(it.sprint) }
            .distinctBy { it.mode to it.holdForward }
            .sortedWith(LaunchSolution.BEST_FIRST)
            .map { TrajectoryDecision.Drop(it.sprint, context.edge.to, it) }
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision as TrajectoryDecision.Drop
        val from = context.body.stance.center()
        val to = (decision.step ?: context.body.stance).center()
        return DropProgram(
            takeoff = interpolate(from, to, decision.solution.launchOffset),
            aim = interpolate(from, to, decision.solution.aimDistance).copy(y = to.y),
            solution = decision.solution,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
        )
    }

    /**
     * A drop is over when it has landed, not when the stance changes.
     *
     * The body crosses into the next column while still walking on the take-off block, and
     * treating that as arrival anchors it in mid-air over a stance that does not exist.
     */
    override fun completed(context: CompletionContext): Boolean = context.airborne

    private val MODES = LaunchMode.entries.filter { it.drops }
}
