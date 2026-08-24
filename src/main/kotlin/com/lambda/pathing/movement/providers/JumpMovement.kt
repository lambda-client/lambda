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
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.*
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * Crossing a gap ballistically.
 *
 * The graph offers these permissively -- a jump here is a *candidate*, masked from geometry
 * and an arc sweep, and the trajectory layer still has to fly it through the real simulator
 * before anything replays it. What the candidate carries now is the take-off that makes it
 * work, so the search refines a solved answer instead of enumerating its way to one.
 */
object JumpMovement : Movement {
    override val id = MovementId.JUMP

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowJumpCandidates) return@buildList

        for ((dx, dz) in WalkMovement.CARDINALS) {
            for (span in 2..options.maxJumpSpan) {
                for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
                    add(spec(dx, dz, span, rise, context.costs.jumpCandidateCost(span, rise)))
                }
            }
        }
        if (options.allowDiagonal) {
            for ((dx, dz) in WalkMovement.DIAGONALS) {
                for (span in 2..options.maxDiagonalJumpSpan) {
                    for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
                        add(spec(dx, dz, span, rise, context.costs.diagonalJumpCandidateCost(span, rise)))
                    }
                }
            }
        }
    }

    private fun spec(dx: Int, dz: Int, span: Int, rise: Int, cost: Double) = TemplateSpec(
        dx = span * dx, dy = rise, dz = span * dz,
        movement = id,
        cost = cost,
        conditions = WalkMovement.stanceConditions(span * dx, rise, span * dz),
        arc = MotionTemplate.ArcSpec(dx, dz, span, rise, MODES),
    )

    /**
     * A launch, and a frame either side of when it should fire.
     *
     * The old vocabulary crossed every gait with seven blind delays -- about thirty
     * rollouts for a move with one right answer. The solved edge already names the gait and
     * the point on the ground to leave from, so all that remains is predicting when the
     * body reaches that point, and bracketing the prediction.
     */
    /**
     * A hop is worth offering on any step that does not descend.
     *
     * Descents are excluded outright: leaping off a ledge adds height, air time and fall
     * distance to a move whose purpose is to lose height, and the drop exists for it.
     */
    override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y >= edge.from.y

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val solutions = solutionsFor(context)
            .filter { context.constraints.sprintModes.contains(it.sprint) }
            .distinctBy { it.mode to it.launchOffset }
            .sortedByDescending { it.margin }

        return solutions.flatMap { solution ->
            val nominal = launchFrame(context, solution)
            LAUNCH_BRACKET
                .map { (nominal + it).coerceAtLeast(0) }
                .distinct()
                .map { delay ->
                    TrajectoryDecision.Launch(solution.sprint, context.edge.to, delay, solution)
                }
        }
    }

    /**
     * The launches worth trying, from the body as it actually is.
     *
     * Two, deliberately. The edge's own solution is the *ideal* take-off, solved against a
     * generic cruising body because when templates are priced there is no body yet, and it
     * is usually right. But it can ask for a speed the body has no room to reach: standing
     * at the lip of a platform with the gap in front of it there is no run-up, and a launch
     * that assumes cruise never fires.
     *
     * So the body's own speed gets a solution too -- "what does this jump look like taken
     * exactly as I am", which from a standstill is a standing jump. Margin orders the two,
     * so the ideal one still leads wherever there is runway to honour it.
     */
    private fun solutionsFor(context: DecisionContext): List<LaunchSolution> {
        val ideal = context.edge.launch ?: hopOver(context)
        val asIs = LaunchSolver.best(
            context.edge.from, context.edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { context.body.speed },
            preferredEntrySpeed = { context.body.speed },
        )
        return listOfNotNull(ideal, asIs)
    }

    /**
     * A jump onto a step the coarse layer expects to be walked.
     *
     * Walking edges carry no solved take-off, but the body still sometimes has to leave the
     * ground on one: a lip the mask called passable, a shape the walk catches on. Solving
     * on demand keeps that capability without the old indiscriminacy -- the launch is aimed
     * at the same target block, and a step no jump can land on gets no jump. Descending
     * steps are excluded outright; that is what the drop is for.
     */
    private fun hopOver(context: DecisionContext): LaunchSolution? {
        val edge = context.edge
        if (edge.to.y < edge.from.y) return null
        val reachable = maxOf(context.body.speed, context.ballistics.cruiseSpeed(sprint = true))
        return LaunchSolver.best(
            edge.from, edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { reachable },
        )
    }

    /**
     * The tick on which the body is predicted to reach the solved take-off point.
     *
     * Reading the current velocity alone answers zero for a body standing still, which is
     * how a standing parkour jump ends up fired on the tick before the run-up that makes it
     * work. Walking the ground recurrence accounts for the acceleration in between.
     */
    private fun launchFrame(context: DecisionContext, solution: LaunchSolution): Int {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
        if (remaining <= 0.0) return 0

        // Speed along the edge rather than raw speed: a body moving across the edge is not
        // closing on the take-off point however fast it is going.
        val closing = alongEdge(
            from, to,
            from.x + body.velocity.x,
            from.z + body.velocity.z,
        ).coerceAtLeast(0.0)

        return context.ballistics.groundRunUpTicks(
            entrySpeed = closing,
            distance = remaining,
            sprint = solution.sprint,
            maxTicks = MAX_LAUNCH_FRAME,
        )
    }

    override fun program(context: ProgramContext): ControlProgram = SegmentFollowerProgram(
        nodes = context.nodes,
        sprint = context.decision.sprint,
        lookAheadNodes = LOOK_AHEAD_NODES,
        launch = context.launch,
        maxYawChange = context.constraints.maxYawDegreesPerFrame,
    )

    /** A jump is over once it has fired and the body has come back down. */
    override fun completed(context: CompletionContext): Boolean {
        val launch = context.launch ?: return context.stance != context.body.stance
        return launch.hasFired && context.airborne
    }

    fun triggerFor(decision: TrajectoryDecision): LaunchTrigger? =
        (decision as? TrajectoryDecision.Launch)?.let { LaunchTrigger(it.delayFrames) }

    private val MODES = LaunchMode.entries.filter { it.jumps }

    /** Frames tried either side of the predicted take-off tick. */
    private val LAUNCH_BRACKET = listOf(0, -1, 1)

    private const val MAX_LAUNCH_FRAME = 8

    private const val LOOK_AHEAD_NODES = 1
}
