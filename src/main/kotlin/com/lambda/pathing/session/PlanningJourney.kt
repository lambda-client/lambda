package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SharedEdges
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.HazardRules
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
	var goal: Stance = origin.finalGoal
		private set

	val hazards: HazardRules get() = origin.moveOptions.hazards

	private val coarseView = world.snapshot.pinned()

	private val goals = origin.waypoints + origin.finalGoal

	private val starts = listOf(origin.start) + origin.waypoints

	private fun buildLegs(remembered: BaritoneCache?): List<CoarsePlanningState> {
		val memory = remembered?.let { coarseView.remembering(it.sectionsExcept(starts + goals), world::chunkCapturable) }
		val shared = SharedEdges(goals)
		return goals.indices.map { index ->
			val alternatives = if (index == goals.lastIndex) origin.alternatives else emptyList()
			CoarsePlanningState(memory ?: coarseView, origin.moveOptions, starts[index], goals[index], memory = memory, alternatives = alternatives, shared = shared.takeIf { alternatives.isEmpty() })
		}
	}

	val coarse = CoarseService(
		world, goals, { buildLegs(remembered) }, starts, origin.coarseExpansionBudget,
		repin = { coarseView.repin(world.snapshot) },
		pinCurrent = { coarseView.pinnedTo(world.snapshot) },
		expandsField = !origin.routeOnly,
	).also { it.start() }

	fun retarget(goal: Stance): Boolean {
		if (origin.waypoints.isNotEmpty()) return false
		if (goal == this.goal) return true
		this.goal = goal
		InterestPrimer.primeGoal(world, goal)
		coarse.retarget(goals.lastIndex, goal)
		return true
	}

	fun cancel() {
		coarse.close()
		world.close()
	}

	fun compatibleWith(preparation: PlanningPreparation): Boolean =
		goal == preparation.finalGoal && origin.moveOptions == preparation.moveOptions &&
				origin.profile.isCompatibleWith(preparation.profile) &&
				origin.waypoints.size >= preparation.waypoints.size &&
				origin.waypoints.takeLast(preparation.waypoints.size) == preparation.waypoints

	fun firstLegFor(preparation: PlanningPreparation): Int = goals.size - (preparation.waypoints.size + 1)

	fun planner(leg: Int): CoarsePlanner = coarse.planner(leg)

	companion object {
		fun open(preparation: PlanningPreparation, player: ClientPlayerEntity): PlanningJourney {
			val terrain = preparation.dumpDirectory?.let { TerrainRecorder(player.entityWorld, TerrainStores.of(player.entityWorld)) }
			val world = PathingWorld(preparation.bounds, MinecraftCaptureSource(player.entityWorld, player, preparation.moveOptions.hazards, preparation.moveOptions.avoidMobs, preparation.moveOptions.mobLingerTicks), terrain)
			InterestPrimer.primeBody(world, player.blockPos)
			InterestPrimer.primeJourney(world, preparation.start, preparation.finalGoal, preparation.waypoints)
			val remembered = if (preparation.useBaritoneCache) BaritoneCache.of(player.entityWorld) else null
			return PlanningJourney(preparation, world, terrain, remembered)
		}
	}
}
