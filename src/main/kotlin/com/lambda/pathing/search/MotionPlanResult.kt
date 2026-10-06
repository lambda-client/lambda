package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos

data class PlanAttempt(
	val simulatedFrames: Int,
	val finalGoalError: Double,
	val diagnostic: TrajectoryDiagnostic?,
)

internal class AttemptAccumulator {
	var count: Int = 0
		private set
	var nearest: PlanAttempt? = null
		private set

	fun record(attempt: PlanAttempt) {
		count++
		if (nearest == null || attempt.finalGoalError < nearest!!.finalGoalError) nearest = attempt
	}
}

enum class SearchExit(private val label: String) {
	SOLVED("solved"),
	BUDGET("budget"),
	EXHAUSTED("exhausted"),
	DRAINED("drained"),
	COMMIT_REFUSED("commit-refused");

	override fun toString(): String = label
}

data class SearchExhaustion(
	val exit: SearchExit,
	val expansions: Int,
	val windowExpansions: Int,
	val windowBudget: Int,
	val temperature: Double,
	val openAnchors: Int,
	val parkedAnchors: Int,
	val blockedAttempts: Int,
	val spentAnchors: Int,
	val tapeRestarts: Int,
	val routeNodes: Int,
	val deepestRouteIndex: Int,
	val commitAttempts: Int = 0,
	val publishRefusals: Int = 0,
	val repairs: Int = 0,
	val firstSolutionFrames: Int = -1,
	val solutionsFound: Int = 0,
) {
	override fun toString(): String = buildString {
		append("exit=").append(exit)
		append(" expansions=").append(expansions)
		append(" window=").append(windowExpansions).append('/').append(windowBudget)
		append(" temp=%.2f".format(temperature))
		append(" open=").append(openAnchors)
		append(" parked=").append(parkedAnchors)
		append(" blocked=").append(blockedAttempts)
		append(" spent=").append(spentAnchors)
		append(" restarts=").append(tapeRestarts)
		append(" route=").append(deepestRouteIndex).append('/').append(routeNodes)
		append(" commits=").append(commitAttempts)
		append(" refusals=").append(publishRefusals)
		if (repairs > 0) append(" repairs=").append(repairs)
		if (solutionsFound > 0) append(" first=").append(firstSolutionFrames).append(" solutions=").append(solutionsFound)
	}
}

sealed interface MotionPlanResult {
	data object Cancelled : MotionPlanResult

	data class Success(
		val sourceRoute: CoarseRoutePlan,
		val tape: InputTape,
		val rollout: TrajectoryRollout,

		val safeAnchorStance: Stance,
		val safeAnchorFrame: Int,

		val frameDependencies: List<Set<VoxelPos>>,

		val dependencies: Set<VoxelPos>,
		val attemptCount: Int,
		val controlSegments: Int = 1,
		val spliceFrames: List<Int> = emptyList(),

		val planSegments: List<PlanSegment> = emptyList(),

		val arrivalTicksEstimate: Double = Double.NaN,
		val comparedRunningArrivalTicks: Double = Double.NaN,
		val comparedRunningSequence: Long = -1,

		val segments: List<TapeSegment> = emptyList(),

		val legTouches: List<LegTouch> = emptyList(),
	) : MotionPlanResult {
		val lowerBoundTicks: Double get() = sourceRoute.lowerBoundTicks

		val excessRatio: Double
			get() = if (lowerBoundTicks > 0.0) rollout.frames.size / lowerBoundTicks else Double.NaN

		fun profile(): String = buildString {
			append("frames=").append(rollout.frames.size)
			append(" bound=%.0f".format(lowerBoundTicks))
			append(" ratio=%.2fx".format(excessRatio))
			segments.groupBy { it.movement }
				.mapValues { (_, v) -> v.sumOf { it.frames } to v.size }
				.entries.sortedByDescending { it.value.first }
				.forEach { (movement, cost) ->
					append(' ').append(movement).append('=').append(cost.first)
					append('/').append(cost.second)
				}
		}
	}

	data class UnsupportedRoute(val movements: Set<MovementId>) : MotionPlanResult

	data class NoSafeStop(
		val attemptCount: Int,
		val nearest: PlanAttempt?,
		val remainingStart: Stance? = null,
		val blockedProgress: Int? = null,
		val exhaustion: SearchExhaustion? = null,
	) : MotionPlanResult

	data class UnstableReplay(val reason: String) : MotionPlanResult
}
