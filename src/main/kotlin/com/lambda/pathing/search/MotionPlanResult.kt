package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import kotlin.math.hypot

data class PlanAttempt(
	val simulatedFrames: Int,
	val finalGoalError: Double,
	val diagnostic: TrajectoryDiagnostic?,
)

internal class AttemptAccumulator(private val probe: SearchProbe) {
	var count: Int = 0
		private set
	var nearest: PlanAttempt? = null
		private set

	private var nearestSquared = Double.POSITIVE_INFINITY

	fun record(rollout: TrajectoryRollout, stopped: Boolean, goal: HorizontalPoint, diagnostic: TrajectoryDiagnostic?) {
		probe.attempt(rollout, stopped, diagnostic)
		count++
		val final = rollout.finalState.position
		val dx = final.x - goal.x
		val dz = final.z - goal.z
		val squared = dx * dx + dz * dz
		if (nearest != null && squared >= nearestSquared) return
		nearestSquared = squared
		nearest = PlanAttempt(rollout.frames.size, hypot(dx, dz), diagnostic)
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
		val plan: TrajectoryPlan,
		val safeAnchorStance: Stance,
		val safeAnchorFrame: Int,
		val attemptCount: Int,
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
