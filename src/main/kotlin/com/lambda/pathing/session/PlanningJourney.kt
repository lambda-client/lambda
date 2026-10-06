package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.streaming.MinecraftCaptureSource
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.client.network.ClientPlayerEntity

internal class PlanningJourney private constructor(
	private val origin: PlanningPreparation,
	val world: PathingWorld,
) {
	val goal: Stance get() = origin.finalGoal

	private val legs: List<CoarsePlanningState> = run {
		val goals = origin.waypoints + origin.finalGoal
		val starts = listOf(origin.start) + origin.waypoints
		goals.indices.map { index ->
			CoarsePlanningState(
				world.snapshot, origin.moveOptions, starts[index], goals[index],
				horizonChunks = origin.planningHorizonChunks,
				frontierSweepBudget = origin.frontierSweepBudget,
				capturable = world::chunkCapturable,
			)
		}
	}

	fun cancel() = world.close()

	fun compatibleWith(preparation: PlanningPreparation): Boolean =
		goal == preparation.finalGoal && origin.moveOptions == preparation.moveOptions &&
				origin.profile.isCompatibleWith(preparation.profile) &&
				origin.planningHorizonChunks == preparation.planningHorizonChunks &&
				origin.waypoints.size >= preparation.waypoints.size &&
				origin.waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	fun legsFor(preparation: PlanningPreparation): List<CoarsePlanningState> =
		legs.takeLast(preparation.waypoints.size + 1)

	companion object {
		fun open(preparation: PlanningPreparation, player: ClientPlayerEntity): PlanningJourney {
			val world = PathingWorld(preparation.bounds, MinecraftCaptureSource(player.entityWorld, player))
			InterestPrimer.primeJourney(world, preparation.start, preparation.finalGoal, preparation.waypoints)
			return PlanningJourney(preparation, world)
		}
	}
}
