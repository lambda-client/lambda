package com.lambda.pathing

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.world.PathingWorld
import com.lambda.util.player.prediction.PlayerPhysicsProfile

internal class PlanningJourney(
    val goal: Stance,
    val moveOptions: SimpleMoveOptions,
    val profile: PlayerPhysicsProfile,
    val cancellation: PlanningCancellation,
    val world: PathingWorld,
    val coarseState: CoarsePlanningState,
) {
    fun cancel() {
        cancellation.cancel()
        world.close()
    }

    fun compatibleWith(preparation: TrajectoryPlanningPreparation): Boolean =
        goal == preparation.finalGoal && moveOptions == preparation.moveOptions &&
            profile.isCompatibleWith(preparation.profile) &&
            coarseState.horizonChunks == preparation.planningHorizonChunks
}
