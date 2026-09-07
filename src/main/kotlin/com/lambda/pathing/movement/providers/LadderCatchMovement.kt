/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement.providers

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.CellPredicate
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.CompletionContext
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.MotionTemplate
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.movement.MovementContext
import com.lambda.pathing.movement.ProgramContext
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.movement.TemplateSpec
import com.lambda.pathing.movement.TrajectoryDecision
import kotlin.math.hypot
import com.lambda.pathing.launch.LaunchSolution

/**
 * A jump or fall whose landing is not a floor but a GRAB: the flight enters a
 * climbable cell and the ladder arrests it. This is the vocabulary a jump-onto-ladder
 * parkour move needs -- jump templates demand a standable landing, so a mid-wall
 * ladder cell was never a legal target even though the graph can stand there (climb
 * movement occupies climbable cells) and the simulator handles the grab.
 *
 * The flight reuses the launch machinery wholesale ([TrajectoryDecision.Launch] with
 * this movement's id): solver, trigger, closed-loop air steering at the ladder cell's
 * centre, rollout certification. Arrival is a stance change into the target cell; the
 * momentum layer prices the arrival as STOPPED ([com.lambda.pathing.coarse.MomentumRules]),
 * which is what a hanging body is, and the onward climb departs from rest exactly as
 * the climb rules demand.
 */
object LadderCatchMovement : Movement {
    override val id = MovementId.LADDER_CATCH

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        if (!context.options.allowClimbing) return@buildList
        for ((dx, dz) in WalkMovement.CARDINALS) {
            for (span in MIN_SPAN..MAX_SPAN) {
                for (rise in MIN_RISE..MAX_RISE) {
                    add(
                        TemplateSpec(
                            dx = dx * span,
                            dy = rise,
                            dz = dz * span,
                            movement = id,
                            cost = context.costs.jumpCandidateCost(span.toDouble(), rise) +
                                CATCH_SETTLE_TICKS,
                            conditions = listOf(
                                CellCondition(dx * span, rise, dz * span, CellPredicate.CLIMBABLE),
                                CellCondition(dx * span, rise + 1, dz * span, CellPredicate.CENTER_HEAD),
                            ),
                            arc = MotionTemplate.ArcSpec(dx * span, dz * span, rise, MODES),
                        )
                    )
                }
            }
        }
    }

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val edge = context.edge
        val reachable = maxOf(context.body.speed, context.ballistics.cruiseSpeed(sprint = true))
        val solutions = listOfNotNull(
            edge.launch,
            LaunchSolver.best(
                edge.from, edge.to,
                profile = context.ballistics,
                modes = MODES,
                maxEntrySpeed = { reachable },
            ),
        ).distinctBy { it.mode to it.launchOffset }
            .filter { context.constraints.sprintModes.contains(it.sprint) }

        return solutions.flatMap { solution ->
            delays(context, solution).map { delay ->
                TrajectoryDecision.Launch(solution.sprint, edge.to, delay, solution, movement = id)
            }
        }
    }

    private fun delays(context: DecisionContext, solution: LaunchSolution): List<Int> {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
        if (remaining <= 0.0) return listOf(0)
        val closing = alongEdge(
            from, to,
            from.x + body.velocity.x,
            from.z + body.velocity.z,
        ).coerceAtLeast(0.0)
        val nominal = context.ballistics.groundRunUpTicks(
            entrySpeed = closing, distance = remaining,
            sprint = solution.sprint, maxTicks = MAX_LAUNCH_FRAME,
        )
        return listOf(nominal, (nominal - 1).coerceAtLeast(0), nominal + 1).distinct()
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision as TrajectoryDecision.Launch
        val solution = decision.solution
        val step = decision.step
        val airPlan = if (solution != null && step != null) {
            val from = context.body.stance
            val dx = (step.x - from.x).toDouble()
            val dz = (step.z - from.z).toDouble()
            val length = hypot(dx, dz)
            if (length > 1e-9) AirSteering.AirPlan(
                aimX = step.x + 0.5,
                aimZ = step.z + 0.5,
                unitX = dx / length,
                unitZ = dz / length,
                airTicks = solution.airTicks,
                holdTicks = if (solution.holdForward) solution.holdTicks else 0,
                sprintAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
                    else BallisticProfile.WALK_AIR_ACCELERATION,
                walkAcceleration = BallisticProfile.WALK_AIR_ACCELERATION,
            ) else null
        } else null
        // Forward stays held throughout: pressing into the ladder is what keeps the
        // caught body in the cell instead of sliding back off the face.
        return SegmentFollowerProgram(
            nodes = context.nodes,
            sprint = decision.sprint,
            lookAheadNodes = 1,
            launch = context.launch,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
            holdForwardInFlight = true,
            airPlan = airPlan,
        )
    }

    override fun completed(context: CompletionContext): Boolean {
        val step = (context.decision as? TrajectoryDecision.Launch)?.step
            ?: return context.stance != context.body.stance
        return context.launch?.hasFired != false && context.stance == step
    }

    override val completesAirborne: Boolean get() = true

    override val pressesIntoTerrain: Boolean get() = true

    override fun descentAllowance(decision: TrajectoryDecision): Double = 1.0

    private val MODES = LaunchMode.entries

    private const val MIN_SPAN = 2

    private const val MAX_SPAN = 3

    /** Falling catches: the grab arrests a short drop onto the ladder face. */
    private const val MIN_RISE = -2

    private const val MAX_RISE = 1

    /** Ticks the caught body spends arresting and settling before it can climb. */
    private const val CATCH_SETTLE_TICKS = 6.0

    private const val MAX_LAUNCH_FRAME = 8
}
