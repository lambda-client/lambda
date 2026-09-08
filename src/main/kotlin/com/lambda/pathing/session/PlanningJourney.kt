package com.lambda.pathing.session

import com.lambda.pathing.TrajectoryPlanningPreparation
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.PathingWorld

/**
 * The world capture and coarse states behind a walk: one streaming world, one coarse
 * state per leg of the route ([waypoints] then [goal]). Survives a replan toward the same
 * final goal, including one made after some waypoints were already passed.
 */
internal class PlanningJourney(
	val goal: Stance,
	/** Walk-through waypoints before [goal], in order. */
	val waypoints: List<Stance>,
	val moveOptions: SimpleMoveOptions,
	val profile: PlayerPhysicsProfile,
	val cancellation: PlanningCancellation,
	val world: PathingWorld,
	/** One per leg: `waypoints[i]` is `legs[i].goal`, the final goal is the last. */
	val legs: List<CoarsePlanningState>,
) {
	init {
		require(legs.size == waypoints.size + 1) { "one coarse state per leg" }
	}

	val coarseState: CoarsePlanningState get() = legs.first()

	fun cancel() {
		cancellation.cancel()
		world.close()
	}

	/** Same destination, options, body, ring, and a route that is a tail of this one. */
	fun compatibleWith(preparation: TrajectoryPlanningPreparation): Boolean =
		goal == preparation.finalGoal && moveOptions == preparation.moveOptions &&
				profile.isCompatibleWith(preparation.profile) &&
				coarseState.horizonChunks == preparation.planningHorizonChunks &&
				waypoints.size >= preparation.waypoints.size &&
				waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	/** The coarse states for the legs [preparation] still has to walk; see [compatibleWith]. */
	fun legsFor(preparation: TrajectoryPlanningPreparation): List<CoarsePlanningState> =
		legs.takeLast(preparation.waypoints.size + 1)
}
