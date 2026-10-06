package com.lambda.pathing.session

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.SearchConfig
import com.lambda.pathing.search.stanceOf
import com.lambda.pathing.search.supportedStanceOf
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.nio.file.Path

internal sealed interface PlanningPreparationResult {
	data class Ready(val preparation: PlanningPreparation) : PlanningPreparationResult
	data class Failed(val failure: PlanningFailure) : PlanningPreparationResult
	data object Cancelled : PlanningPreparationResult
}

internal data class PlanningPreparation(
	val finalGoal: Stance,

	val waypoints: List<Stance>,
	val bounds: SimulationSnapshotBounds,
	val moveOptions: SimpleMoveOptions,
	val seedConfig: MotionConstraints,
	val initial: MovementSimulationState,
	val profile: PlayerPhysicsProfile,
	val start: Stance,
	val planningHorizonChunks: Int,
	val searchConfig: SearchConfig,
	val coarseExpansionBudget: Int,
	val frontierSweepBudget: Int,
	val plannerThreads: Int,
	val dumpDirectory: Path?,

	val dumpAllPlans: Boolean,
	val startedMillis: Long,
)

internal fun resolveGoalStance(player: ClientPlayerEntity, goal: Stance): Stance {
	val world = player.entityWorld
	if (!world.isChunkLoaded(goal.x shr 4, goal.z shr 4)) return goal
	val pos = BlockPos(goal.x, goal.y, goal.z)
	val shape = world.getBlockState(pos).getCollisionShape(world, pos)
	if (shape.isEmpty) return goal
	val surface = shape.getMax(Direction.Axis.Y)
	return if (surface > 0.0 && surface <= CoarseMoveRates.FREE_STEP_RISE) {
		goal.offset(0, 1, 0)
	} else goal
}

internal fun preparePlanning(
	player: ClientPlayerEntity,
	request: PathingRequest,
	goal: Stance,
	waypoints: List<Stance>,
	cancellation: PlanningCancellation,
	initialOverride: MovementSimulationState? = null,
): PlanningPreparationResult {
	val started = System.currentTimeMillis()
	val config = request.pathingConfig
	val turnSpeed = request.rotationConfig.turnSpeed

	@Suppress("NAME_SHADOWING")
	val goal = resolveGoalStance(player, goal)
	val resolvedWaypoints = waypoints.map { resolveGoalStance(player, it) }
	val moveOptions = SimpleMoveOptions(
		allowDiagonal = config.allowDiagonal,
		allowStepUp = config.allowStepUp,
		maxWalkOffDepth = config.maxWalkOffDepth,
		maxDropSpan = config.maxDropSpan,
		allowClimbing = config.allowClimbing,
		allowJumpCandidates = config.allowJumpCandidates,
		maxJumpSpan = config.maxJumpSpan,
		maxJumpDrop = config.maxJumpDrop,
		allowOffAxisJumps = config.allowOffAxisJumps,
		allowDeepDropJumps = config.allowDeepDropJumps,
		allowSlimeBounces = config.allowSlimeBounces,
		maxBounceDrop = config.maxBounceDrop,
	)
	val seedConfig = MotionConstraints(
		maxFrames = config.maxFrames,
		maxYawDegreesPerFrame = turnSpeed,
		goalRadius = config.goalRadius,
		touchArrival = config.touchArrival,
		maxSafeFallDistance = config.maxSafeFallDistance,
		sprintModes = if (config.allowSprint) listOf(true, false) else listOf(false),
	)
	val initial = initialOverride ?: MovementSimulationState.from(player)
	val profile = PlayerPhysicsProfile.capture(player)
	val start = supportedStanceOf(initial) ?: stanceOf(initial)

	if (cancellation.isCancelled) return PlanningPreparationResult.Cancelled

	if (!plannableEntry(initial.velocity)) {
		return PlanningPreparationResult.Failed(
			PlanningFailure.InvalidRequest(
				"entry velocity %.3f b/t is outside what the planner simulates"
					.format(initial.velocity.horizontalLength())
			)
		)
	}

	if (!player.entityWorld.worldBorder.contains(BlockPos(goal.x, goal.y, goal.z))) {
		return PlanningPreparationResult.Failed(
			PlanningFailure.InvalidRequest("destination is outside the world border")
		)
	}

	val world = player.entityWorld
	val bounds = SimulationSnapshotBounds(
		minX = Int.MIN_VALUE,
		minY = world.bottomY,
		minZ = Int.MIN_VALUE,
		maxX = Int.MAX_VALUE,
		maxY = world.bottomY + world.height - 1,
		maxZ = Int.MAX_VALUE,
	)
	if (goal.y !in bounds.simulableStanceY) {
		return PlanningPreparationResult.Failed(
			PlanningFailure.InvalidRequest("destination is outside the simulable build height")
		)
	}

	val dumpDirectory = if (config.dumpFailedPlans || config.dumpAllPlans || java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")) {
		MinecraftClient.getInstance().runDirectory.toPath().resolve(DUMP_DIRECTORY)
	} else {
		null
	}
	return PlanningPreparationResult.Ready(
		PlanningPreparation(
			finalGoal = goal,
			waypoints = resolvedWaypoints,
			bounds = bounds,
			moveOptions = moveOptions,
			seedConfig = seedConfig,
			initial = initial,
			profile = profile,
			start = start,
			planningHorizonChunks = config.planningHorizonChunks,
			searchConfig = SearchConfig.horizon(
				runwayFrames = config.horizonRunwayFrames,
				commitFrames = config.horizonCommitFrames,
				maxExpansions = config.trajectoryExpansionBudget,
				bootstrapDelayMillis = config.bootstrapDelayMillis.toLong(),
			),
			coarseExpansionBudget = config.coarseExpansionBudget,
			frontierSweepBudget = config.frontierSweepBudget,
			plannerThreads = config.plannerThreads,
			dumpDirectory = dumpDirectory,
			dumpAllPlans = config.dumpAllPlans,
			startedMillis = started,
		)
	)
}

private fun plannableEntry(velocity: Vec3d): Boolean =
	velocity.horizontalLength() <= MAX_ENTRY_HORIZONTAL_SPEED &&
			velocity.y in -MAX_ENTRY_DESCENT_SPEED..MAX_ENTRY_ASCENT_SPEED

private const val MAX_ENTRY_HORIZONTAL_SPEED = 0.6

private const val MAX_ENTRY_ASCENT_SPEED = 0.5

private const val MAX_ENTRY_DESCENT_SPEED = 4.0

private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"
