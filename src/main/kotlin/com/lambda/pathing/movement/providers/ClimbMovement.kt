package com.lambda.pathing.movement.providers

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.*
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.CoarseVoxelView

object ClimbMovement : Movement {
    override val id = MovementId.CLIMB

    override fun templates(context: MovementContext): List<TemplateSpec> {
        if (!context.options.allowClimbing) return emptyList()
        return buildList {

            add(
                TemplateSpec(
                    dx = 0, dy = 1, dz = 0,
                    movement = id,
                    cost = context.costs.climbCost(1),
                    conditions = listOf(
                        CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, 1, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, 2, 0, CellPredicate.CENTER_HEAD),
                    ),
                )
            )
            add(
                TemplateSpec(
                    dx = 0, dy = -1, dz = 0,
                    movement = id,
                    cost = context.costs.climbCost(1),
                    conditions = listOf(
                        CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        CellCondition(0, -1, 0, CellPredicate.CLIMBABLE),
                    ),
                )
            )

            for ((dx, dz) in WalkMovement.CARDINALS) {
                add(
                    TemplateSpec(
                        dx = dx, dy = -1, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(dx, -1, dz, CellPredicate.CLIMBABLE),

                            CellCondition(dx, 0, dz, CellPredicate.CENTER_HEAD),
                        ),
                    )
                )
            }

            for ((dx, dz) in WalkMovement.CARDINALS) {
                add(
                    TemplateSpec(
                        dx = dx, dy = 0, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(dx, 0, dz, CellPredicate.CLIMBABLE),
                            CellCondition(dx, 1, dz, CellPredicate.CENTER_HEAD),
                        ),
                    )
                )
                add(
                    TemplateSpec(
                        dx = dx, dy = 0, dz = dz,
                        movement = id,
                        cost = context.costs.climbCost(1),
                        conditions = listOf(
                            CellCondition(0, 0, 0, CellPredicate.CLIMBABLE),
                        ) + WalkMovement.stanceConditions(dx, 0, dz),
                    )
                )
            }
        }
    }

    override fun occupies(view: CoarseVoxelView, stance: Stance): Boolean =
        view.medium(stance.x, stance.y, stance.z) == Medium.CLIMBABLE

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val edge = context.edge
        val vertical = edge.from.x == edge.to.x && edge.from.z == edge.to.z

        if (!vertical) {

            val enteringOverLip = edge.to.y < edge.from.y
            val yaw = bearingBetween(edge.from.center(), edge.to.center())
            return listOf(
                TrajectoryDecision.Climb(
                    step = edge.to,
                    holdForward = true,
                    holdWhileClimbing = !enteringOverLip,
                    climbWithJump = false,
                    yaw = yaw,
                )
            )
        }

        val hold = supportBearing(context.view, edge.from, edge.to)
        val ascending = edge.to.y > edge.from.y
        return listOf(
            TrajectoryDecision.Climb(
                step = edge.to,

                holdForward = ascending && hold != null,
                holdWhileClimbing = ascending && hold != null,

                climbWithJump = ascending,

                yaw = hold,
            )
        )
    }

    private fun supportBearing(
        view: CoarseVoxelView,
        from: Stance,
        to: Stance,
    ): Double? {
        val support = WalkMovement.CARDINALS.firstOrNull { (dx, dz) ->
            view.medium(from.x + dx, from.y, from.z + dz) == Medium.SOLID &&
                view.medium(to.x + dx, to.y, to.z + dz) == Medium.SOLID
        } ?: return null
        val (dx, dz) = support
        return bearingBetween(from.center(), from.offset(dx, 0, dz).center())
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision as TrajectoryDecision.Climb
        return ClimbProgram(
            targetYaw = decision.yaw,
            holdForward = decision.holdForward,
            holdWhileClimbing = decision.holdWhileClimbing,
            climbWithJump = decision.climbWithJump,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
            takeoff = context.body.stance.center(),
            aim = (decision.step ?: context.body.stance).center(),
        )
    }

    @Suppress("ReturnCount")
    override fun completed(context: CompletionContext): Boolean {
        val target = (context.decision as? TrajectoryDecision.Climb)?.step
            ?: return context.stance != context.body.stance

        return context.stance == target
    }

    override val completesAirborne: Boolean get() = true

    override val pressesIntoTerrain: Boolean get() = true

}
