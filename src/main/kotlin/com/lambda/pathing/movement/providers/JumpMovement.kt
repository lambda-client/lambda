package com.lambda.pathing.movement.providers

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.CoarseMoveRates
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.movement.MotionTemplate
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

        val closing = closingSpeed(context)
        val standard = solutions.flatMap { solution ->
            val nominal = launchFrame(context, solution)
            LAUNCH_BRACKET
                .map { (nominal + it).coerceAtLeast(0) }
                .distinct()
                .filter { delay -> !hopelesslySlow(context.ballistics, closing, delay, solution) }
                .map { delay ->
                    TrajectoryDecision.Launch(solution.sprint, context.edge.to, delay, solution)
                }
        }
        return standard + runUpDecisions(context, solutions, closing)
    }

    private fun runUpDecisions(
        context: DecisionContext,
        solutions: List<LaunchSolution>,
        closing: Double,
    ): List<TrajectoryDecision> {
        if (solutions.isEmpty()) return emptyList()
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val along = alongEdge(from, to, body.position.x, body.position.z)

        val anyReachable = solutions.any { solution ->
            val available = (solution.launchOffset - along).coerceAtLeast(0.0)
            val ticks = context.ballistics.groundRunUpTicks(
                entrySpeed = closing, distance = available,
                sprint = solution.sprint, maxTicks = MAX_LAUNCH_FRAME,
            )
            context.ballistics.runUpSpeed(closing, ticks, solution.sprint) >=
                solution.speed - solution.speedSlack * 0.5
        }
        if (anyReachable) return emptyList()

        val ballistics = context.ballistics
        val decisions = ArrayList<TrajectoryDecision>()
        for (solution in solutions) {
            val groundNeeded = ballistics.runUpDistanceFor(solution.speed, solution.sprint)
            if (groundNeeded != null) {
                val retreatAlong = solution.launchOffset - (groundNeeded + RUN_UP_MARGIN_BLOCKS)
                if (clearBehind(context, -retreatAlong)) {
                    decisions += TrajectoryDecision.RunUpLaunch(
                        solution.sprint, edge.to, solution, retreatAlong, hopAlong = null,
                    )
                }
                continue
            }

            // entry beyond what ground running reaches: build it with a preparatory hop
            val mode = if (solution.sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
            val cruise = ballistics.cruiseSpeed(solution.sprint)
            val hop = ballistics.fly(mode, cruise, 0.0) ?: continue
            if (hop.exitSpeed < solution.speed - solution.speedSlack * 0.5 - HOP_EXIT_TOLERANCE) continue
            val groundToCruise = ballistics.runUpDistanceFor(cruise * CRUISE_FRACTION, solution.sprint)
                ?: continue
            for (gap in HOP_LANDING_GAPS) {
                val hopAlong = solution.launchOffset - hop.distance - gap
                val retreatAlong = hopAlong - groundToCruise - RUN_UP_MARGIN_BLOCKS
                if (!clearBehind(context, -retreatAlong)) continue
                decisions += TrajectoryDecision.RunUpLaunch(
                    solution.sprint, edge.to, solution, retreatAlong, hopAlong,
                )
            }
        }
        return decisions.take(MAX_RUN_UP_VARIANTS)
    }

    private fun clearBehind(context: DecisionContext, distance: Double): Boolean {
        if (distance <= 0.0) return true
        return retreatClear(context, distance)
    }

    private fun retreatClear(context: DecisionContext, distance: Double): Boolean {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
        if (length <= 1e-9) return false
        val unitX = (to.x - from.x) / length
        val unitZ = (to.z - from.z) / length
        val view = context.view
        var sampled = 1.0
        while (sampled <= distance + 1.0) {
            val x = kotlin.math.floor(from.x - unitX * sampled).toInt()
            val z = kotlin.math.floor(from.z - unitZ * sampled).toInt()
            val y = edge.from.y
            val standable = view.standingSurface(x, y - 1, z) != null &&
                view.voxel(x, y, z).centerPassable &&
                view.voxel(x, y + 1, z).centerPassable
            if (!standable) return false
            sampled += 1.0
        }
        return true
    }

    private fun closingSpeed(context: DecisionContext): Double {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        return alongEdge(
            from, to,
            from.x + body.velocity.x,
            from.z + body.velocity.z,
        ).coerceAtLeast(0.0)
    }

    private fun hopelesslySlow(
        ballistics: BallisticProfile,
        closing: Double,
        delay: Int,
        solution: LaunchSolution,
    ): Boolean {
        val achievable = ballistics.runUpSpeed(closing, delay, solution.sprint)
        return solution.speed - solution.speedSlack - achievable > HOPELESS_SPEED_DEFICIT
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

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision
        if (decision is TrajectoryDecision.RunUpLaunch) {
            val from = context.body.stance.center()
            val to = (decision.step ?: context.body.stance).center()
            return RunUpLaunchProgram(
                takeoff = from,
                aim = to,
                solution = decision.solution,
                retreatAlong = decision.retreatAlong,
                hopAlong = decision.hopAlong,
                maxYawChange = context.constraints.maxYawDegreesPerFrame,
            )
        }
        return SegmentFollowerProgram(
            nodes = context.nodes,
            sprint = context.decision.sprint,
            lookAheadNodes = LOOK_AHEAD_NODES,
            launch = context.launch,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
        )
    }

    override fun completed(context: CompletionContext): Boolean {
        val decision = context.decision
        if (decision is TrajectoryDecision.RunUpLaunch) {
            if (!context.airborne) return false
            val from = context.body.stance.center()
            val to = (decision.step ?: context.body.stance).center()
            val along = alongEdge(from, to, context.observed.position.x, context.observed.position.z)
            return along > decision.solution.launchOffset + POST_LAUNCH_MARGIN_BLOCKS
        }
        val launch = context.launch ?: return context.stance != context.body.stance
        return launch.hasFired && context.airborne
    }

    override fun transitionFrames(decision: TrajectoryDecision): Int {
        val runUp = decision as? TrajectoryDecision.RunUpLaunch ?: return 0
        val retreatFrames = (-runUp.retreatAlong).coerceAtLeast(0.0) * RETREAT_FRAMES_PER_BLOCK
        return RUN_UP_TRANSITION_FRAMES + retreatFrames.toInt() + runUp.solution.airTicks
    }

    fun triggerFor(decision: TrajectoryDecision): LaunchTrigger? =
        (decision as? TrajectoryDecision.Launch)?.let { LaunchTrigger(it.delayFrames) }

    private val MODES = LaunchMode.entries.filter { it.jumps }

    private val LAUNCH_BRACKET = listOf(0, -1, 1)

    private const val MAX_LAUNCH_FRAME = 8

    private const val HOPELESS_SPEED_DEFICIT = 0.10

    private const val RUN_UP_MARGIN_BLOCKS = 0.75

    private const val RUN_UP_TRANSITION_FRAMES = 50

    private const val RETREAT_FRAMES_PER_BLOCK = 10

    private const val CRUISE_FRACTION = 0.97

    private const val POST_LAUNCH_MARGIN_BLOCKS = 1.0

    private const val HOP_EXIT_TOLERANCE = 0.02

    private val HOP_LANDING_GAPS = listOf(-0.35, -0.1, 0.2)

    private const val MAX_RUN_UP_VARIANTS = 4

    private const val LOOK_AHEAD_NODES = 1
}
