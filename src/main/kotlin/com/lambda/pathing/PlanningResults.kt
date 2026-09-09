package com.lambda.pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import java.nio.file.Path

sealed interface PlanningFailure {
	val message: String

	data class InvalidRequest(override val message: String) : PlanningFailure
	data class ResourceLimit(override val message: String) : PlanningFailure
	data class NoRoute(override val message: String) : PlanningFailure

	data class NoCertifiedMotion(override val message: String) : PlanningFailure
}

sealed interface PathPlanResult {
	data class Planned(val path: PublishedPath) : PathPlanResult
	data class Failed(val failure: PlanningFailure) : PathPlanResult
	data object Cancelled : PathPlanResult
}

internal sealed interface PlanningPreparationResult {
	data class Ready(val preparation: TrajectoryPlanningPreparation) : PlanningPreparationResult
	data class Failed(val failure: PlanningFailure) : PlanningPreparationResult
	data object Cancelled : PlanningPreparationResult
}

internal data class TrajectoryPlanningPreparation(
	val finalGoal: Stance,

	val waypoints: List<Stance>,
	val bounds: SimulationSnapshotBounds,
	val moveOptions: SimpleMoveOptions,
	val seedConfig: MotionConstraints,
	val initial: MovementSimulationState,
	val profile: PlayerPhysicsProfile,
	val start: Stance,
	val planningHorizonChunks: Int,
	val horizonRunwayFrames: Int,
	val horizonCommitFrames: Int,
	val coarseExpansionBudget: Int,
	val trajectoryExpansionBudget: Int,
	val frontierSweepBudget: Int,
	val bootstrapDelayMillis: Long,
	val plannerThreads: Int,
	val improvementBudget: Int,
	val dumpDirectory: Path?,

	val dumpAllPlans: Boolean,
	val startedMillis: Long,
)
