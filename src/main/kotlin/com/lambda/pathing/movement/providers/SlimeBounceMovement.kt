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
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.momentumSpeed
import com.lambda.pathing.movement.*
import kotlin.math.hypot

/**
 * Falling onto slime to get somewhere nothing else reaches.
 *
 * A jump has only its own impulse and covers about four and a half blocks. A drop gives up
 * every block of height it uses. A bounce does neither: the fall supplies the impulse, and
 * the rebound hands most of the height back, so six to ten blocks of ground opens up at a
 * landing height a fall could never finish at.
 *
 * The edges are wide because the arc is long -- thirty-odd ticks -- and that length is also
 * why they are offered sparingly. A bounce is only proposed where a bouncy cell is actually
 * under the arc's trough, which [com.lambda.pathing.coarse.BounceArcProbe] checks; without
 * slime in the right place none of these templates produce an edge at all, so the cost on
 * ordinary terrain is the probe's refusal and nothing else.
 */
object SlimeBounceMovement : Movement {
    override val id = MovementId.BOUNCE

    /**
     * One template per (offset, fall depth), over the range the arcs actually cover.
     *
     * Enumerated over depth as well as offset because the depth *is* the impulse: the same
     * landing is reachable from a deeper pit with less run-up, and from a shallow one only
     * with a long approach. Which of those a given piece of terrain offers is not something a
     * template can know, so both are proposed and the probe decides.
     *
     * The spans are asked of the ballistics rather than chosen. A bounce cannot be short --
     * even leaving the lip at a standstill, a six-deep fall carries the body six blocks,
     * because thirty ticks of flight give air acceleration plenty of time -- and for any given
     * (depth, landing height) the reachable window is only about a block and a half wide. So
     * enumerating spans blindly would propose hundreds of offsets per direction and have the
     * solver refuse all but two of them. Asking first turns a three-dimensional enumeration
     * into a list barely longer than the number of depths.
     */
    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowSlimeBounces) return@buildList
        val profile = context.ballistics

        for (drop in MIN_DROP..options.maxBounceDrop) {
            // A landing above the trough but below the lip: the rebound never reaches the
            // height it fell from, and a landing at the bottom is a drop, not a bounce.
            for (rise in -(drop - MIN_RISE_BELOW_LIP)..-MIN_RISE_BELOW_LIP) {
                val window = reachWindow(profile, drop, rise) ?: continue

                for ((dx, dz) in WalkMovement.CARDINALS + WalkMovement.DIAGONALS) {
                    val perStep = hypot(dx.toDouble(), dz.toDouble())
                    for (span in 1..MAX_SPAN) {
                        val distance = span * perStep
                        if (distance < window.first || distance > window.second) continue
                        add(spec(dx, dz, span, drop, rise, context))
                    }
                }
            }
        }
    }

    /**
     * The distances a bounce of this shape can be made to cover, at zero speed and at full.
     *
     * Both ends come from the same affine relation the solver inverts, so this is the exact
     * feasible window rather than an estimate of one.
     */
    private fun reachWindow(
        profile: BallisticProfile,
        drop: Int,
        rise: Int,
    ): Pair<Double, Double>? {
        val standing = profile.bounce(0.0, drop, rise, holdForward = true, sprint = true) ?: return null
        val unit = profile.bounce(1.0, drop, rise, holdForward = true, sprint = true) ?: return null
        val slope = unit.distance - standing.distance
        if (slope <= 0.0) return null
        // Shifted by the take-off offset, because a template's span is measured between
        // stance centres while the arc is measured from where the body actually leaves.
        val offset = BounceSolver.LAUNCH_OFFSET
        return standing.distance + offset to
            standing.distance + offset + slope * profile.momentumSpeed(sprint = true)
    }

    private fun spec(
        dx: Int,
        dz: Int,
        span: Int,
        drop: Int,
        rise: Int,
        context: MovementContext,
    ) = TemplateSpec(
        dx = span * dx,
        dy = rise,
        dz = span * dz,
        movement = id,
        cost = context.costs.bounceCost(span, drop, rise),
        conditions = WalkMovement.stanceConditions(span * dx, rise, span * dz),
        arc = MotionTemplate.ArcSpec(
            dx = span * dx,
            dz = span * dz,
            rise = rise,
            bounceDrop = drop,
        ),
    )

    /** Only a descent, and only one deep enough that a fall could feed a rebound. */
    override fun offersFor(edge: CoarseEdge): Boolean =
        edge.bounce != null || edge.to.y < edge.from.y - 1

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val solution = context.edge.bounce ?: return emptyList()
        if (!context.constraints.sprintModes.contains(solution.sprint)) return emptyList()
        return listOf(TrajectoryDecision.Bounce(solution.sprint, context.edge.to, solution))
    }

    /**
     * The fall's own depth, which the route knows nothing about.
     *
     * A bounce edge's endpoints are both above the slime it uses, so the evaluator's floor --
     * derived from the route nodes -- sits above the whole middle of the move. Without this
     * every bounce rollout is rejected as having fallen below its route on the frame it
     * begins to work.
     */
    override fun descentAllowance(decision: TrajectoryDecision): Double {
        val bounce = decision as? TrajectoryDecision.Bounce ?: return 0.0
        return bounce.solution.drop.toDouble()
    }

    /**
     * The solved flight, plus room for the approach that feeds it and the settle after.
     *
     * Taken from the arc rather than fixed, because a deep bounce is half again as long as a
     * shallow one and a single number would either cut the deep ones off or let every other
     * movement wander.
     */
    override fun transitionFrames(decision: TrajectoryDecision): Int {
        val bounce = decision as? TrajectoryDecision.Bounce ?: return 0
        return bounce.solution.airTicks + TRANSITION_MARGIN_FRAMES
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision as TrajectoryDecision.Bounce
        val from = context.body.stance.center()
        val to = (decision.step ?: context.body.stance).center()
        return BounceProgram(
            takeoff = from,
            aim = to.copy(y = to.y),
            solution = decision.solution,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
        )
    }

    /**
     * A bounce is over when the body has settled, not when it first touches the ground.
     *
     * The slime contact *is* a grounded frame, in the middle of the move. Treating it as
     * arrival would anchor the body on top of the slime it was supposed to be leaving --
     * which is the same shape of mistake a drop made by treating its stance change as
     * arrival, and it fails the same way.
     */
    override fun completed(context: CompletionContext): Boolean =
        context.airborne && context.observed.onGround &&
            context.observed.velocity.y <= SETTLED_VERTICAL_SPEED &&
            context.stance == (context.decision.step ?: context.stance)

    /**
     * Frames either side of the flight: the run-up to the lip, and the settle on landing.
     *
     * The approach is the longer half -- a bounce wants speed, and building it from rest
     * takes a dozen ticks.
     */
    private const val TRANSITION_MARGIN_FRAMES = 20

    /** Below a rebound and above the press a standing body makes into the floor. */
    private const val SETTLED_VERTICAL_SPEED = 0.1

    /** Past the reach of the deepest fall, so the window always bites before this does. */
    private const val MAX_SPAN = 12

    /** A one-block fall has no rebound worth planning around. */
    private const val MIN_DROP = 3

    /** The rebound never reaches the lip, so a landing there is unsolvable by construction. */
    private const val MIN_RISE_BELOW_LIP = 2
}
