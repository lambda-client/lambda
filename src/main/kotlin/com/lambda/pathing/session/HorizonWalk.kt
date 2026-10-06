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
import com.lambda.pathing.search.TrajectoryPlanId
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.concurrent.atomic.AtomicLong

private val planIds = AtomicLong()

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

	val searchField: ValueField =
		if (legs != null && field is CoarseValueField) SwitchableValueField(field) else field

	val result = SearchLoop(
		route, planner.moves.catalog, searchField, initial, profile, snapshot, seedConfig, searchConfig,
		onSafePrefix = { step ->
			if (!cancelled()) {
				val sequence = published + 1
				val path = publishedPath(
					step, step.sourceRoute, profile, planIds.incrementAndGet(),
					System.currentTimeMillis() - started, partial = true,
					finalGoal = finalGoal,
					planningGeneration = planningGeneration,
					publicationSequence = sequence,
				)
				publish(path, published > 0)
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
		is MotionPlanResult.Success -> PathPlanResult.Planned(
			publishedPath(
				result, result.sourceRoute, profile, planIds.incrementAndGet(),
				System.currentTimeMillis() - started, partial = false,
				finalGoal = finalGoal,
				planningGeneration = planningGeneration,
				publicationSequence = published + 1,
			)
		)

		else -> PathPlanResult.Failed(
			PlanningFailure.NoCertifiedMotion(
				"the search dead-ended (${describeRefusal(result)})",
			)
		)
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

private fun publishedPath(
	seed: MotionPlanResult.Success,
	route: CoarseRoutePlan,
	profile: PlayerPhysicsProfile,
	id: Long,
	planMillis: Long,
	partial: Boolean,
	finalGoal: Stance,
	planningGeneration: Long,
	publicationSequence: Int,
) = PublishedPath(
	route = route,
	plan = TrajectoryPlan.fromWalkingSeed(TrajectoryPlanId(id), seed, profile),
	safeAnchorStance = seed.safeAnchorStance,
	safeAnchorFrame = seed.safeAnchorFrame,
	attempts = seed.attemptCount,
	planMillis = planMillis,
	finalGoal = finalGoal,
	partial = partial || route.goal != finalGoal,
	planningGeneration = planningGeneration,
	publicationSequence = publicationSequence,
	arrivalTicksEstimate = seed.arrivalTicksEstimate,
	comparedRunningArrivalTicks = seed.comparedRunningArrivalTicks,
	comparedRunningSequence = seed.comparedRunningSequence,
	legTouches = seed.legTouches,
)
