package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.store.BaritoneCache
import com.lambda.pathing.world.store.TerrainRecorder
import com.lambda.pathing.world.store.TerrainStores
import com.lambda.pathing.world.streaming.MinecraftCaptureSource
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.client.network.ClientPlayerEntity

internal class PlanningJourney private constructor(
	private val origin: PlanningPreparation,
	val world: PathingWorld,
	val terrain: TerrainRecorder?,
	remembered: BaritoneCache?,
) {
	val goal: Stance get() = origin.finalGoal

	private val legs: List<CoarsePlanningState> = run {
		val goals = origin.waypoints + origin.finalGoal
		val starts = listOf(origin.start) + origin.waypoints
		goals.indices.map { index ->
			val memory = remembered?.let { world.snapshot.remembering(it.sectionsExcept(starts + goals), it::remembers) }
			CoarsePlanningState(
				memory ?: world.snapshot, origin.moveOptions, starts[index], goals[index],
				frontierSweepBudget = origin.frontierSweepBudget,
				capturable = world::chunkCapturable,
				memory = memory,
			)
		}
	}

	val coarse = CoarseService(world, legs, listOf(origin.start) + origin.waypoints, origin.coarseExpansionBudget).also { it.start() }

	fun cancel() {
		coarse.close()
		world.close()
	}

	fun compatibleWith(preparation: PlanningPreparation): Boolean =
		goal == preparation.finalGoal && origin.moveOptions == preparation.moveOptions &&
				origin.profile.isCompatibleWith(preparation.profile) &&
				origin.waypoints.size >= preparation.waypoints.size &&
				origin.waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	fun firstLegFor(preparation: PlanningPreparation): Int = legs.size - (preparation.waypoints.size + 1)

	fun planner(leg: Int): CoarsePlanner = legs[leg].planner

	companion object {
		fun open(preparation: PlanningPreparation, player: ClientPlayerEntity): PlanningJourney {
			val terrain = preparation.dumpDirectory?.let { TerrainRecorder(player.entityWorld, TerrainStores.of(player.entityWorld)) }
			val world = PathingWorld(preparation.bounds, MinecraftCaptureSource(player.entityWorld, player), terrain)
			InterestPrimer.primeJourney(world, preparation.start, preparation.finalGoal, preparation.waypoints)
			val remembered = if (preparation.useBaritoneCache) BaritoneCache.of(player.entityWorld) else null
			return PlanningJourney(preparation, world, terrain, remembered)
		}
	}
}
