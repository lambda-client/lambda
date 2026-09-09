package com.lambda.pathing.session

import com.lambda.pathing.TrajectoryPlanningPreparation
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.PathingWorld

internal class PlanningJourney(
	val goal: Stance,

	val waypoints: List<Stance>,
	val moveOptions: SimpleMoveOptions,
	val profile: PlayerPhysicsProfile,
	val cancellation: PlanningCancellation,
	val world: PathingWorld,

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

	fun compatibleWith(preparation: TrajectoryPlanningPreparation): Boolean =
		goal == preparation.finalGoal && moveOptions == preparation.moveOptions &&
				profile.isCompatibleWith(preparation.profile) &&
				coarseState.horizonChunks == preparation.planningHorizonChunks &&
				waypoints.size >= preparation.waypoints.size &&
				waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	fun legsFor(preparation: TrajectoryPlanningPreparation): List<CoarsePlanningState> =
		legs.takeLast(preparation.waypoints.size + 1)
}
