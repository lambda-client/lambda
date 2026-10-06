package com.lambda.pathing.session

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.LegSource
import com.lambda.pathing.search.MotionPlanResult
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchClock
import com.lambda.pathing.search.SearchConfig
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.search.SearchLoop
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.SystemSearchClock
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal fun walkHorizon(
	route: CoarseRoutePlan,
	planner: CoarsePlanner,
	initial: MovementSimulationState,
	profile: PlayerPhysicsProfile,
	snapshot: SnapshotSimulationEnvironment,
	seedConfig: MotionConstraints,
	execution: ExecutionFeedback = ExecutionFeedback.DETACHED,
	publish: (PublishedPath, Boolean) -> Unit,
	started: Long,
	searchConfig: SearchConfig = SearchConfig.horizon(),
	world: WorldLink? = null,
	clock: SearchClock = SystemSearchClock(),
	cancelled: () -> Boolean = { false },
	planningGeneration: Long = 0L,
	finalGoal: Stance = route.goal,
	field: ValueField = planner.valueField(),
	probe: SearchProbe = SearchProbe.NONE,
	onExhaustion: ((SearchStats) -> Unit)? = null,
	parallelism: Int = 1,
	legs: LegSource? = null,
): PathPlanResult {
	var published = 0

	fun stamp(seed: MotionPlanResult.Success, partial: Boolean, sequence: Int): PublishedPath {
		val planMillis = System.currentTimeMillis() - started
		return PublishedPath(
			route = seed.sourceRoute,
			plan = TrajectoryPlan.fromWalkingSeed(seed, profile),
			safeAnchorStance = seed.safeAnchorStance,
			safeAnchorFrame = seed.safeAnchorFrame,
			attempts = seed.attemptCount,
			planMillis = planMillis,
			finalGoal = finalGoal,
			partial = partial || seed.sourceRoute.goal != finalGoal,
			planningGeneration = planningGeneration,
			publicationSequence = sequence,
			swap = seed.swap,
			legTouches = seed.legTouches,
		)
	}

	val searchField: ValueField =
		if (legs != null && field is CoarseValueField) SwitchableValueField(field) else field

	val result = SearchLoop(
		route, planner.moves.catalog, searchField, initial, profile, snapshot, seedConfig, searchConfig,
		onSafePrefix = { step ->
			if (!cancelled()) {
				publish(stamp(step, partial = true, sequence = published + 1), published > 0)
				published++
			}
		},
		execution = execution,
		clock = clock,
		cancelled = cancelled,
		world = world,
		finalGoal = finalGoal,
		probe = probe,
		onExhaustion = onExhaustion,
		parallelism = parallelism,
		legs = legs,
	).run()

	return when (result) {
		MotionPlanResult.Cancelled -> PathPlanResult.Cancelled
		is MotionPlanResult.Success -> PathPlanResult.Planned(stamp(result, partial = false, sequence = published + 1))

		else -> PathPlanResult.Failed("the search dead-ended (${describeRefusal(result)})")
	}
}

private fun describeRefusal(result: MotionPlanResult): String = when (result) {
	is MotionPlanResult.NoSafeStop -> buildString {
		append(result.attemptCount).append(" attempts")
		append(", deepest route index ").append(result.exhaustion.deepestRouteIndex)
		result.remainingStart?.let { append(", stalled at ").append(it) }
		result.nearest?.let { nearest ->
			append(
				", nearest miss %.2f blocks after %d frames"
					.format(nearest.finalGoalError, nearest.simulatedFrames),
			)
			nearest.diagnostic?.let { append(" (").append(it).append(")") }
		}
		append("; ").append(result.exhaustion)
	}
	is MotionPlanResult.UnstableReplay -> "unstable replay: ${result.reason}"
	is MotionPlanResult.UnsupportedRoute -> "unsupported movements ${result.movements}"
	else -> result::class.simpleName ?: "unknown"
}
