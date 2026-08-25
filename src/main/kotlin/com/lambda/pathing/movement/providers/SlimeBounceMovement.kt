package com.lambda.pathing.movement.providers

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.MotionTemplate
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.momentumSpeed
import com.lambda.pathing.movement.*
import kotlin.math.hypot

object SlimeBounceMovement : Movement {
    override val id = MovementId.BOUNCE

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowSlimeBounces) return@buildList
        val profile = context.ballistics

        for (drop in MIN_DROP..options.maxBounceDrop) {

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

    private fun reachWindow(
        profile: BallisticProfile,
        drop: Int,
        rise: Int,
    ): Pair<Double, Double>? {
        val standing = profile.bounce(0.0, drop, rise, holdForward = true, sprint = true) ?: return null
        val unit = profile.bounce(1.0, drop, rise, holdForward = true, sprint = true) ?: return null
        val slope = unit.distance - standing.distance
        if (slope <= 0.0) return null

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

    override fun offersFor(edge: CoarseEdge): Boolean =
        edge.bounce != null || edge.to.y < edge.from.y - 1

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val solution = context.edge.bounce ?: return emptyList()
        if (!context.constraints.sprintModes.contains(solution.sprint)) return emptyList()
        return listOf(TrajectoryDecision.Bounce(solution.sprint, context.edge.to, solution))
    }

    override fun descentAllowance(decision: TrajectoryDecision): Double {
        val bounce = decision as? TrajectoryDecision.Bounce ?: return 0.0
        return bounce.solution.drop.toDouble()
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

    override fun completed(context: CompletionContext): Boolean =
        context.airborne && context.observed.onGround &&
            context.observed.velocity.y <= SETTLED_VERTICAL_SPEED &&
            context.stance == (context.decision.step ?: context.stance)

    private const val TRANSITION_MARGIN_FRAMES = 20

    private const val SETTLED_VERTICAL_SPEED = 0.1

    private const val MAX_SPAN = 12

    private const val MIN_DROP = 3

    private const val MIN_RISE_BELOW_LIP = 2
}
