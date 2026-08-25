package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.ProposalContext
import com.lambda.pathing.movement.TrajectoryDecision

internal class ActionSet(
    private val catalog: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
) {
    fun actions(anchor: ValueAnchor): List<TrajectoryDecision> {

        if (anchor.parent != null && field.guide(anchor.stance) <= searchConfig.finishValueTicks) {
            return emptyList()
        }

        val steps = field.steps(
            anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
            anchor.heading(),
        )
        if (steps.isEmpty()) return emptyList()

        val context = ProposalContext(
            body = anchor,
            steps = steps,
            constraints = config,
            view = field.view,
            steering = field,
            headingFanDegrees = searchConfig.headingFanDegrees,
        )
        val walks = ArrayList<TrajectoryDecision>()
        val launches = ArrayList<TrajectoryDecision>()
        for (movement in catalog.movements) {
            val proposals = movement.proposals(context)
            walks += proposals.walks
            launches += proposals.launches
        }

        val first = steps.first()
        val jumpFirst = catalog[first.movement]?.id != MovementId.WALK

        val descents = ArrayList<TrajectoryDecision>()
        for (step in steps.take(LAUNCH_STEPS)) {
            val decisions = movementDecisions(anchor, step)
            if (step.to.y < anchor.stance.y) descents += decisions else launches += decisions
        }

        return if (jumpFirst) descents + launches + walks else walks + descents + launches
    }

    private fun movementDecisions(anchor: ValueAnchor, edge: CoarseEdge): List<TrajectoryDecision> {
        val owner = catalog[edge.movement]
        val context = DecisionContext(anchor, edge, config, field.view)
        return buildList {

            if (owner != null && owner.id != MovementId.WALK) addAll(owner.decisions(context))
            catalog.movements.forEach { movement ->
                if (movement !== owner && movement.offersFor(edge)) addAll(movement.decisions(context))
            }
        }
    }

    private companion object {

        private const val LAUNCH_STEPS = 2
    }
}
