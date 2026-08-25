package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import kotlin.math.abs

internal class ActionSet(
    private val catalog: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
) {
    fun actions(anchor: ValueAnchor, hazardFrame: Int?): List<TrajectoryDecision> {

        if (anchor.parent != null && field.guide(anchor.stance) <= searchConfig.finishValueTicks) {
            return emptyList()
        }

        val steps = field.steps(
            anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
            anchor.heading(),
        )
        if (steps.isEmpty()) return emptyList()

        val actions = ArrayList<TrajectoryDecision>()
        val walks = ArrayList<TrajectoryDecision>()
        val launches = ArrayList<TrajectoryDecision>()
        val walking = catalog[MovementId.WALK]
        for (step in steps) {
            walking?.let { walks += it.decisions(DecisionContext(anchor, step, config, field.view)) }
        }

        val bearing = descentBearing(anchor, steps.first().to)
        val first = steps.first()
        val target = first.to

        val jumpFirst = catalog[first.movement]?.id != MovementId.WALK

        for (sprint in config.sprintModes) {
            actionsForOffsets(anchor, sprint, target, bearing, walks)

            if (turnsAhead(anchor, steps.first().to)) {
                for ((keys, facingOffset) in DECOUPLED_FACINGS) {
                    walks += TrajectoryDecision.Heading(
                        sprint, target, bearing + facingOffset, delayFrames = null, keys = keys,
                    )
                }
            }
        }

        for (sprint in config.sprintModes) {
            for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                if (delay != 0 && anchor.hazardFrame == null) continue
                launches += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = delay)
            }
            if (anchor.hazardFrame != null || turnsAhead(anchor, target)) {
                for (offset in searchConfig.headingFanDegrees) {
                    if (offset == 0.0) continue
                    for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing + offset, delayFrames = delay,
                        )
                    }
                }
            }

            if (hazardFrame != null) {
                for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                    for (air in AIRBORNE_KEYS.drop(1)) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing, delayFrames = delay,
                            keys = MovementKeys.FORWARD, airborneKeys = air,
                        )
                    }
                }
            }
        }

        val descents = ArrayList<TrajectoryDecision>()
        for (step in steps.take(LAUNCH_STEPS)) {
            val decisions = movementDecisions(anchor, step)
            if (step.to.y < anchor.stance.y) descents += decisions else launches += decisions
        }

        actions += if (jumpFirst) descents + launches + walks else walks + descents + launches
        return actions
    }

    private fun turnsAhead(anchor: ValueAnchor, firstStep: Stance): Boolean {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD + 1, anchor.heading())
        if (chain.size < 3) return false
        val first = bearingBetween(chain[0].center(), chain[1].center())
        val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
        return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
    }

    private fun descentBearing(anchor: ValueAnchor, firstStep: Stance): Double {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD, anchor.heading())
        val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
        return bearingBetween(
            HorizontalPoint(anchor.state.position.x, anchor.state.position.y, anchor.state.position.z),
            aim,
        )
    }

    private fun actionsForOffsets(
        anchor: ValueAnchor,
        sprint: Boolean,
        target: Stance,
        bearing: Double,
        into: MutableList<TrajectoryDecision>,
    ) {
        into += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = null)
        if (anchor.hazardFrame == null && !turnsAhead(anchor, target)) return
        for (offset in searchConfig.headingFanDegrees) {
            if (offset == 0.0) continue
            into += TrajectoryDecision.Heading(sprint, target, bearing + offset, delayFrames = null)
        }
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

        private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

        private val DECOUPLED_FACINGS = listOf(
            MovementKeys.FORWARD_RIGHT to -45.0,
            MovementKeys.FORWARD_LEFT to 45.0,
        )

        private const val DECOUPLE_TURN_DEGREES = 35.0

        private val AIRBORNE_KEYS = listOf(
            MovementKeys.FORWARD, MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT,
        )

        private const val BEARING_LOOKAHEAD = 2

        private const val LAUNCH_STEPS = 2
    }
}
