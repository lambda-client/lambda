package com.lambda.pathing.movement.providers

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.MotionTemplate
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.momentumSpeed
import com.lambda.pathing.movement.*
import kotlin.math.abs
import kotlin.math.hypot

object SlimeBounceMovement : Movement {
    override val id = MovementId.BOUNCE

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowSlimeBounces) return@buildList
        val profile = context.ballistics

        for (drop in MIN_DROP..options.maxBounceDrop) {

            // Physics gates the rise, not the template: the window is null wherever
            // no launch style can deliver the landing. Jump launches reach a block
            // below the lip; walking off never lands closer than two below it.
            for (rise in -(drop - 1)..MAX_RISE) {
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

    private fun reachWindow(
        profile: BallisticProfile,
        drop: Int,
        rise: Int,
    ): Pair<Double, Double>? {
        val depth = drop.toDouble()
        val height = rise.toDouble()
        val offset = BounceSolver.LAUNCH_OFFSET

        // The solver tries every launch style, so the offer window spans them all: each
        // jump and walk-off line contributes its floor (the GENTLEST launch: standing,
        // forward released) and its ceiling (standing sprint-hold plus full momentum).
        // A rise only a jump can deliver has no walk-off line to contribute.
        var floor = Double.POSITIVE_INFINITY
        var ceiling = Double.NEGATIVE_INFINITY
        for (jump in listOf(true, false)) {
            val standing =
                profile.bounce(0.0, depth, height, holdForward = true, sprint = true, jump = jump) ?: continue
            val unit =
                profile.bounce(1.0, depth, height, holdForward = true, sprint = true, jump = jump) ?: continue
            val slope = unit.distance - standing.distance
            if (slope <= 0.0) continue
            val gentle = profile.bounce(0.0, depth, height, holdForward = false, sprint = false, jump = jump)
            floor = minOf(floor, (gentle ?: standing).distance + offset)
            ceiling = maxOf(
                ceiling,
                standing.distance + offset + slope * profile.momentumSpeed(sprint = true),
            )
        }
        return if (floor <= ceiling) floor to ceiling else null
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

    override fun offersFor(edge: CoarseEdge): Boolean =
        edge.bounce != null || edge.to.y < edge.from.y - 1

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val solution = context.edge.bounce ?: return emptyList()
        if (!context.constraints.sprintModes.contains(solution.sprint)) return emptyList()
        return listOf(TrajectoryDecision.Bounce(solution.sprint, context.edge.to, solution))
    }

    override fun descentAllowance(decision: TrajectoryDecision): Double {
        val bounce = decision as? TrajectoryDecision.Bounce ?: return 0.0
        return bounce.solution.contactDepth
    }

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

    override fun completed(context: CompletionContext): Boolean {
        if (!context.airborne || !context.observed.onGround) return false
        if (context.observed.velocity.y > SETTLED_VERTICAL_SPEED) return false
        val step = context.decision.step ?: return true
        // The landing window is a full block wide by design and the launch carries a
        // few tenths of execution spread, so the body may settle the cell past or
        // beside the templated landing. Any settle at the landing HEIGHT within one
        // cell is this bounce arriving; an undershoot into the pit settles lower and
        // still fails.
        return context.stance.y == step.y &&
            abs(context.stance.x - step.x) <= 1 &&
            abs(context.stance.z - step.z) <= 1
    }

    private const val TRANSITION_MARGIN_FRAMES = 20

    private const val SETTLED_VERTICAL_SPEED = 0.1

    private const val MAX_SPAN = 12

    /**
     * The shallowest STANCE drop offered. Stance deltas are not physical heights (a real
     * 2.44 fall can read drop 2); the physics window and the probe judge each template,
     * this only bounds the fan. See docs/decisions/launch-solver.md (minimum stance drop).
     */
    private const val MIN_DROP = 2

    /** Landing level with the lip needs more rebound than any drop's reflection keeps. */
    private const val MAX_RISE = 0
}
