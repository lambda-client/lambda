package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.concurrent.ExecutorService

object ValueFieldAnchorSearch {

	fun search(
		route: CoarseRoutePlan,
		catalog: MovementCatalog,
		field: ValueField,
		initialState: MovementSimulationState,
		profile: PlayerPhysicsProfile,
		environment: SnapshotSimulationEnvironment,
		config: MotionConstraints = MotionConstraints(),
		searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
		onSafePrefix: ((MotionPlanResult.Success) -> Unit)? = null,
		cursorFrame: (() -> Int?)? = null,
		clock: SearchClock = SystemSearchClock(),
		cancelled: () -> Boolean = { false },

		worldWait: ((Long) -> Boolean)? = null,

		worldSync: ((CoarseRoutePlan) -> WorldSyncResult)? = null,

		sectionCapturable: ((Int, Int) -> Boolean)? = null,

		expandGuide: ((Double) -> Unit)? = null,

		adoptedSequence: (() -> Long)? = null,

		finalGoal: Stance? = null,

		probe: SearchProbe = SearchProbe.NONE,

		onExhaustion: ((SearchExhaustion) -> Unit)? = null,

		parallelism: Int = 1,
		executor: ExecutorService? = null,
		nextLeg: ((Stance) -> LegHandoff?)? = null,
		hasNextLeg: () -> Boolean = { false },
	): MotionPlanResult {
		if (cancelled()) return MotionPlanResult.Cancelled
		val unsupported = route.edges.mapTo(HashSet()) { it.movement }
			.filterTo(HashSet()) { !catalog.supports(it) }
		if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

		return AnchorSearchSession(
			route, catalog, field, initialState, profile, environment, config, searchConfig,
			onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
			expandGuide, adoptedSequence, finalGoal, probe, onExhaustion,
			parallelism = if (executor != null) parallelism.coerceAtLeast(1) else 1,
			executor = executor,
			nextLeg = nextLeg,
			hasNextLeg = hasNextLeg,
		).run()
	}

	internal fun stanceOf(state: MovementSimulationState): Stance =
		Stance.of(state.position, state.onGround)

	internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
		if (!state.onGround) return null
		val support = state.supportingBlockPos ?: return null
		val base = Stance.of(state.position, true)
		if (support.x == base.x && support.z == base.z) return null
		return Stance(support.x, support.y + 1, support.z)
	}
}
