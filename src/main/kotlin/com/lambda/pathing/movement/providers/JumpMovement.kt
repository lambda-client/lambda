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

object JumpMovement : Movement {
    override val id = MovementId.JUMP

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

    private fun launchFrame(context: DecisionContext, solution: LaunchSolution): Int {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
        if (remaining <= 0.0) return 0

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

    override fun completed(context: CompletionContext): Boolean {
        val launch = context.launch ?: return context.stance != context.body.stance
        return launch.hasFired && context.airborne
    }

    fun triggerFor(decision: TrajectoryDecision): LaunchTrigger? =
        (decision as? TrajectoryDecision.Launch)?.let { LaunchTrigger(it.delayFrames) }

    private val MODES = LaunchMode.entries.filter { it.jumps }

    private val LAUNCH_BRACKET = listOf(0, -1, 1)

    private const val MAX_LAUNCH_FRAME = 8

    private const val LOOK_AHEAD_NODES = 1
}
