package com.lambda.pathing.movement.providers

import com.lambda.pathing.movement.TemplateSpec
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.CellPredicate
import com.lambda.pathing.movement.CompletionContext
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.HeadingFollowerProgram
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.movement.MovementContext
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.ProgramContext
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.movement.center

object WalkMovement : Movement {
    override val id = MovementId.WALK

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        val costs = context.costs
        for ((dx, dz) in CARDINALS) {
            add(spec(dx, 0, dz, costs.cardinalWalk, stanceConditions(dx, 0, dz)))

            if (options.allowStepUp) {
                add(
                    spec(
                        dx, 1, dz, costs.stepUp,
                        stanceConditions(dx, 1, dz) + CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE),
                        MovementId.STEP_UP,
                        strideCost = costs.cardinalWalk,
                    )
                )
            }

            if (options.maxWalkOffDepth >= 1) {
                add(
                    spec(
                        dx, -1, dz, costs.walkOffCost(1),
                        stanceConditions(dx, -1, dz) +
                            (1 downTo 0).map { y -> CellCondition(dx, y, dz, CellPredicate.CENTER_SLICE) },
                        MovementId.WALK_OFF,
                    )
                )
            }
        }

        if (options.allowDiagonal) {
            for ((dx, dz) in DIAGONALS) {

                add(
                    spec(
                        dx, 0, dz, costs.diagonalWalk,
                        stanceConditions(dx, 0, dz) + listOf(
                            CellCondition(dx, 0, 0, CellPredicate.FULL_SLICE),
                            CellCondition(dx, 1, 0, CellPredicate.FULL_HEAD),
                            CellCondition(0, 0, dz, CellPredicate.FULL_SLICE),
                            CellCondition(0, 1, dz, CellPredicate.FULL_HEAD),
                        ),
                    )
                )
            }
        }
    }

    private fun spec(
        dx: Int,
        dy: Int,
        dz: Int,
        cost: Double,
        conditions: List<CellCondition>,
        movement: MovementId = id,
        strideCost: Double? = null,
    ) = TemplateSpec(dx, dy, dz, movement, cost, conditions, strideCost = strideCost)

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> = buildList {
        for (sprint in context.constraints.sprintModes) {
            for ((lookAhead, ease) in WALK_STYLES) {
                add(TrajectoryDecision.Walk(sprint, context.edge.to, lookAhead, ease))
            }
        }
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision
        if (decision is TrajectoryDecision.Heading) {
            return HeadingFollowerProgram(
                targetYaw = decision.yaw,
                sprint = decision.sprint && decision.keys.sustainsSprint,
                maxYawChange = context.constraints.maxYawDegreesPerFrame,
                launch = context.launch,
                keys = decision.keys,
                airborneKeys = decision.airborneKeys,
            )
        }
        return SegmentFollowerProgram(
            nodes = context.nodes,
            sprint = decision.sprint,
            lookAheadNodes = (decision as? TrajectoryDecision.Walk)?.lookAheadNodes ?: DEFAULT_LOOK_AHEAD,
            launch = context.launch,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
            easeTurns = (decision as? TrajectoryDecision.Walk)?.easeTurns == true,
        )
    }

    override fun completed(context: CompletionContext): Boolean {
        val decision = context.decision
        if (context.launch != null) return context.launch.hasFired && context.airborne
        if (decision is TrajectoryDecision.Heading) {
            return context.frameIndex + 1 >= context.headingCommitFrames
        }
        return context.stance != context.body.stance
    }

    fun stanceConditions(dx: Int, dy: Int, dz: Int) = listOf(
        CellCondition(dx, dy - 1, dz, CellPredicate.SUPPORT),
        CellCondition(dx, dy, dz, CellPredicate.CENTER_SLICE),
        CellCondition(dx, dy + 1, dz, CellPredicate.CENTER_HEAD),
    )

    val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    private const val DEFAULT_LOOK_AHEAD = 1
}
