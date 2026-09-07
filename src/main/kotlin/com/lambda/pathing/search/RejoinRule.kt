package com.lambda.pathing.search

import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * When a body that arrived elsewhere counts as having reached a junction: on the same
 * footing, within one beam position bucket and one beam speed bucket. The one rule both
 * [PlanGraph] (which junctions are cut points) and [PlanImprover] (which crossings rejoin)
 * read. See docs/decisions/improver.md.
 */
internal object RejoinRule {
    const val REJOIN_POSITION_TOLERANCE_BLOCKS = 0.25

    const val REJOIN_VELOCITY_TOLERANCE_BLOCKS = 0.075

    /** Vertical or horizontal displacement, whichever is larger. */
    fun positionError(a: MovementSimulationState, b: MovementSimulationState): Double = maxOf(
        abs(a.position.y - b.position.y),
        hypot(a.position.x - b.position.x, a.position.z - b.position.z),
    )

    fun velocityError(a: MovementSimulationState, b: MovementSimulationState): Double =
        hypot(a.velocity.x - b.velocity.x, a.velocity.z - b.velocity.z)

    fun rejoins(candidate: MovementSimulationState, junction: MovementSimulationState): Boolean =
        candidate.onGround == junction.onGround &&
            positionError(candidate, junction) <= REJOIN_POSITION_TOLERANCE_BLOCKS &&
            velocityError(candidate, junction) <= REJOIN_VELOCITY_TOLERANCE_BLOCKS

    /**
     * A junction another chain can be steered into under [rejoins]: a grounded body.
     * Mid-air there is no state to aim for -- a launch is re-solved, not matched.
     */
    fun rejoinable(state: MovementSimulationState): Boolean = state.onGround
}
