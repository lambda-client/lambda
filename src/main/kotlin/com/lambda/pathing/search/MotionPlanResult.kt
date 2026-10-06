package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import kotlin.math.hypot

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

	fun record(rollout: TrajectoryRollout, goal: HorizontalPoint, diagnostic: TrajectoryDiagnostic?) {
		val final = rollout.finalState.position
		val attempt = PlanAttempt(rollout.frames.size, hypot(final.x - goal.x, final.z - goal.z), diagnostic)
		count++
		val current = nearest
		if (current == null || attempt.finalGoalError < current.finalGoalError) nearest = attempt
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

data class SearchStats(
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
	val commitAttempts: Int,
	val publishRefusals: Int,
	val repairs: Int,
	val firstSolutionFrames: Int,
	val solutionsFound: Int,
	val admitted: Int,
	val merged: Int,
	val rootFrame: Int,
	val publishedFrame: Int,
	val horizonEnd: Int,
	val cursorFrame: Int,
	val bestScore: Int?,
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

		val attemptCount: Int,
		val planSegments: List<PlanSegment> = emptyList(),

		val arrivalTicksEstimate: Double = Double.NaN,
		val comparedRunningArrivalTicks: Double = Double.NaN,
		val comparedRunningSequence: Long = -1,

		val legTouches: List<LegTouch> = emptyList(),
	) : MotionPlanResult

	data class UnsupportedRoute(val movements: Set<MovementId>) : MotionPlanResult

	data class NoSafeStop(
		val attemptCount: Int,
		val nearest: PlanAttempt?,
		val remainingStart: Stance?,
		val exhaustion: SearchStats,
	) : MotionPlanResult

	data class UnstableReplay(val reason: String) : MotionPlanResult
}
