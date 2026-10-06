package com.lambda.pathing.session

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.client.network.ClientPlayerEntity

internal class PlanningJourney(
	val goal: Stance,
	val waypoints: List<Stance>,
	val moveOptions: SimpleMoveOptions,
	val profile: PlayerPhysicsProfile,
	val world: PathingWorld,
	val legs: List<CoarsePlanningState>,
) {
	init {
		require(legs.size == waypoints.size + 1) { "one coarse state per leg" }
	}

	val coarseState: CoarsePlanningState get() = legs.first()

	fun cancel() = world.close()

	fun compatibleWith(preparation: PlanningPreparation): Boolean =
		goal == preparation.finalGoal && moveOptions == preparation.moveOptions &&
				profile.isCompatibleWith(preparation.profile) &&
				coarseState.horizonChunks == preparation.planningHorizonChunks &&
				waypoints.size >= preparation.waypoints.size &&
				waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	fun legsFor(preparation: PlanningPreparation): List<CoarsePlanningState> =
		legs.takeLast(preparation.waypoints.size + 1)

	companion object {
		private fun legs(preparation: PlanningPreparation, world: PathingWorld): List<CoarsePlanningState> {
			val goals = preparation.waypoints + preparation.finalGoal
			val starts = listOf(preparation.start) + preparation.waypoints
			return goals.indices.map { index ->
				CoarsePlanningState(
					world.snapshot, preparation.moveOptions, starts[index], goals[index],
					horizonChunks = preparation.planningHorizonChunks,
					frontierSweepBudget = preparation.frontierSweepBudget,
					capturable = world::chunkCapturable,
				)
			}
		}

		fun open(
			preparation: PlanningPreparation,
			player: ClientPlayerEntity,
		): PlanningJourney {
			val world = PathingWorld(preparation.bounds, player.entityWorld, player)
			InterestPrimer.primeJourney(world, preparation.start, preparation.finalGoal, preparation.waypoints)
			return PlanningJourney(
				goal = preparation.finalGoal,
				waypoints = preparation.waypoints,
				moveOptions = preparation.moveOptions,
				profile = preparation.profile,
				world = world,
				legs = legs(preparation, world),
			)
		}
	}
}
