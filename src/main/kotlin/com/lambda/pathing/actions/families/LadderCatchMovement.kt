/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.actions.families

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.SegmentFollowerProgram
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.airPlanToward
import com.lambda.pathing.actions.families.jump.JumpLaunchDelays
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
        for ((dx, dz) in StanceRules.CARDINALS) {
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
        // A catch has no floor to land on: the body must reach the plank while its feet
        // are still inside the ladder cell, and a slow arc arrives there already falling
        // (it hangs an instant, slides below the rung, and drops). Prefer the fastest
        // entry the run-up allows; the plank stops any overshoot.
        val catches = LaunchSolver.solve(
            edge.from, edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { reachable },
            catch = true,
        )
        // Keep the preferred catch and coarse fallback first, but retain other gaits.
        // Equal catch margins do not imply equal reachability from the actual body:
        // a stopped body at the pad's front edge may need the sprint-jump impulse.
        val solutions = (listOfNotNull(catches.firstOrNull(), edge.launch) + catches.drop(1))
            .distinctBy { it.mode to it.launchOffset }
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
        // Aimed at the ladder cell's centre with no lateral offset: the catch has no
        // dodge line, the grab needs the body square on the face.
        val airPlan = if (solution != null && step != null) {
            airPlanToward(context.body.stance, step, solution, lateralOffset = 0.0)
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
            holdJumpInFlight = true,
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

    private const val MAX_LAUNCH_FRAME = JumpLaunchDelays.MAX_LAUNCH_FRAME
}
