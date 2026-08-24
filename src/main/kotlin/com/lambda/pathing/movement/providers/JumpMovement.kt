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
import com.lambda.pathing.coarse.CoarseMoveRates
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.MotionTemplate
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.*
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs

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

    /**
     * Every landing the solver can reach from a cruise approach, rather than eight rays.
     *
     * Two things were wrong with the old enumeration and they shared a cause: it described
     * reach as an integer *span* along a unit direction. That made the compass rays the only
     * possible landings -- three across and one to the side had no template at all -- and it
     * made "how far can this jump go" a count of cells rather than something the ballistics
     * had a say in.
     *
     * The bound is now [LaunchSolver] itself, asked whether it can solve the move for a body
     * arriving at ordinary cruising speed. Nothing is compared against a hand-derived
     * distance, which is what went wrong the first time this was tightened: a flight distance
     * and a centre-to-centre offset are not the same quantity. The body leaves the ground
     * past the stance centre and may land anywhere on the target rather than on its middle,
     * so the reach between cell centres is meaningfully longer than the arc it flies, and
     * comparing the two refused jumps that are entirely ordinary.
     *
     * Asking at cruise rather than at full momentum is the distinction that matters in play.
     * A four-block jump lands from a normal approach; a five-block one needs the body to
     * arrive already carrying more speed than walking gives it, which it may or may not have
     * had room to build. Offering the latter unconditionally is what produced jumps that
     * certified in the planner and then did not happen.
     *
     * Rise falls out of the same question for free, which a span cap could never do: a rising
     * four-block jump needs a run-up and is refused, while a descending five-block one is
     * offered, because that is what the arcs actually do.
     *
     * Generosity is still cheap in the way that matters -- a template is a proposal, and
     * [JumpArcProbe] discards whatever the terrain blocks before the search ever sees it.
     */
    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowJumpCandidates) return@buildList

        val reach = options.maxJumpSpan
        for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (!offered(dx, dz, options)) continue
                    add(spec(dx, dz, rise, context.costs.jumpCandidateCost(hypot(dx, dz), rise)))
                }
            }
        }
    }

    private fun offered(dx: Int, dz: Int, options: SimpleMoveOptions): Boolean {
        val distance = hypot(dx, dz)
        if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
        if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
        if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false

        // `maxJumpSpan` is a span, and a landing may sit a step to the side of it: a gap of
        // n blocks crossed with one cell of lateral offset is the same jump, and refusing it
        // was what made an off-axis landing need the span raised before it appeared.
        //
        // A bound and not merely the physics, because reach costs more than the templates it
        // creates. Every extra block of it lowers the cheapest ticks-per-block any move
        // achieves, and that number is the heuristic -- a graph that can cross five blocks in
        // one leap cannot admissibly claim that crossing five blocks is expensive. The search
        // then expands more of the field for every route, and incremental repair after a
        // chunk arrival costs more. Reach past what is asked for is paid for on every path,
        // including the ones that never jump.
        return distance <= hypot(options.maxJumpSpan, 1) + REACH_EPSILON
    }

    private const val REACH_EPSILON = 1e-9

    private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

    private fun spec(dx: Int, dz: Int, rise: Int, cost: Double) = TemplateSpec(
        dx = dx, dy = rise, dz = dz,
        movement = id,
        cost = cost,
        conditions = WalkMovement.stanceConditions(dx, rise, dz),
        arc = MotionTemplate.ArcSpec(dx, dz, rise, MODES),
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
