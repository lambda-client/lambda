package com.lambda.pathing.session

import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.families.NeoMovement
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.fallLimits
import com.lambda.pathing.search.SearchConfig
import com.lambda.pathing.search.stanceOf
import com.lambda.pathing.search.footprintStances
import com.lambda.pathing.search.supportedStanceOf
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.util.FolderRegistry
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal sealed interface PlanningPreparationResult {
	data class Ready(val preparation: PlanningPreparation) : PlanningPreparationResult
	data class Failed(val reason: String) : PlanningPreparationResult
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
	val searchConfig: SearchConfig,
	val coarseExpansionBudget: Int,
	val plannerThreads: Int,
	val dumpDirectory: Path?,
	val dumpAllPlans: Boolean,
	val liveImprover: Boolean,
	val useBaritoneCache: Boolean,
	val startedMillis: Long,
	val alternatives: List<Stance> = emptyList(),
	val routeOnly: Boolean = false,
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

private const val UNLIMITED_FALL_DAMAGE = 100_000

internal fun PlayerPhysicsProfile.planned(config: PathingConfig): PlayerPhysicsProfile =
	if (config.unlimitedAir && canDrown) copy(canDrown = false) else this

internal fun preparePlanning(
	player: ClientPlayerEntity,
	request: PathingRequest,
	goal: Stance,
	waypoints: List<Stance>,
	initialOverride: MovementSimulationState? = null,
): PlanningPreparationResult {
	val started = System.currentTimeMillis()
	val config = request.pathingConfig
	val turnSpeed = request.rotationConfig.turnSpeed

	@Suppress("NAME_SHADOWING")
	val goal = resolveGoalStance(player, goal)
	val resolvedWaypoints = waypoints.map { resolveGoalStance(player, it) }
	val acceptedFallDamage = when {
		!config.allowFallDamage -> 0
		config.unlimitedFallDamage -> UNLIMITED_FALL_DAMAGE
		else -> (config.maxFallDamage * Vanilla.HEALTH_PER_HEART).toInt()
	}
	val fallLimits = player.fallLimits(acceptedDamage = acceptedFallDamage)
	val airReserve = (Vanilla.MAX_AIR - (config.diveSeconds * Vanilla.TICKS_PER_SECOND).roundToInt()).coerceIn(0, Vanilla.MAX_AIR - 1)
	val profile = PlayerPhysicsProfile.capture(player).planned(config)
	val sprints = config.allowSprint && !config.saveFood && profile.canSprint
	val moveOptions = SimpleMoveOptions(
		allowDiagonal = config.allowDiagonal,
		allowStepUp = config.allowStepUp,
		maxWalkOffDepth = config.maxWalkOffDepth,
		maxDropSpan = config.maxDropSpan,
		allowClimbing = config.allowClimbing,
		allowSwimming = config.allowSwimming,
		allowDiving = config.allowDiving,
		allowJumpCandidates = config.allowJumpCandidates,
		maxJumpSpan = config.maxJumpSpan,
		maxJumpDrop = config.maxJumpDrop,
		allowOffAxisJumps = config.allowOffAxisJumps,
		allowDeepDropJumps = config.allowDeepDropJumps,
		allowSlimeBounces = config.allowSlimeBounces,
		maxBounceDrop = config.maxBounceDrop,
		allowDeepFalls = config.allowDeepFalls,
		allowNeoJumps = config.allowNeoJumps && sprints && turnSpeed >= NeoMovement.MIN_TURN_DEGREES_PER_FRAME,
		fallLimits = fallLimits,
		hazards = config.hazards,
		avoidMobs = config.avoidMobs,
		mobLingerTicks = (config.mobLingerSeconds * Vanilla.TICKS_PER_SECOND).roundToLong(),
		airReserve = airReserve,
		jumpPenaltyTicks = if (config.saveFood) SAVE_FOOD_JUMP_TICKS else 0.0,
		frontierTicksPerBlock = config.frontierTicksPerBlock,
	)
	val seedConfig = MotionConstraints(
		maxFrames = config.maxFrames,
		maxYawDegreesPerFrame = turnSpeed,
		goalRadius = config.goalRadius,
		touchArrival = config.touchArrival,
		touchRestRadius = config.touchRestRadius,
		fallLimits = fallLimits,
		sprintModes = if (sprints) listOf(true, false) else listOf(false),
		jumpPenaltyTicks = if (config.saveFood) SAVE_FOOD_JUMP_TICKS else 0.0,
		avoidLavaEdges = config.avoidLavaEdges,
		airReserve = airReserve,
	)
	val initial = initialOverride ?: MovementSimulationState.from(player)
	val start = supportedStanceOf(initial) ?: footing(player, initial) ?: stanceOf(initial)

	if (!plannableEntry(initial.velocity)) {
		return PlanningPreparationResult.Failed(
			"entry velocity %.3f b/t is outside what the planner simulates".format(initial.velocity.horizontalLength())
		)
	}

	if (!player.entityWorld.worldBorder.contains(BlockPos(goal.x, goal.y, goal.z))) {
		return PlanningPreparationResult.Failed("destination is outside the world border")
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
		return PlanningPreparationResult.Failed("destination is outside the simulable build height")
	}

	val dumpDirectory = if (config.dumpFailedPlans || config.dumpAllPlans || java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")) {
		FolderRegistry.pathingDumps
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
			searchConfig = SearchConfig.horizon(
				runwayFrames = config.horizonRunwayFrames,
				commitFrames = config.horizonCommitFrames,
				windowExpansions = config.windowExpansionBudget,
				bootstrapDelayMillis = config.bootstrapDelayMillis.toLong(),
			),
			coarseExpansionBudget = config.coarseExpansionBudget,
			plannerThreads = config.plannerThreads,
			dumpDirectory = dumpDirectory,
			dumpAllPlans = config.dumpAllPlans,
			liveImprover = config.liveImprover,
			useBaritoneCache = config.useBaritoneCache,
			startedMillis = started,
		)
	)
}

private fun plannableEntry(velocity: Vec3d): Boolean =
	velocity.horizontalLength() <= MAX_ENTRY_HORIZONTAL_SPEED && velocity.y <= MAX_ENTRY_ASCENT_SPEED

private const val MAX_ENTRY_HORIZONTAL_SPEED = 0.6

private const val MAX_ENTRY_ASCENT_SPEED = 0.5

private const val SAVE_FOOD_JUMP_TICKS = 20.0

private fun footing(player: ClientPlayerEntity, initial: MovementSimulationState): Stance? {
	if (!initial.onGround || standsOn(player, stanceOf(initial))) return null
	return footprintStances(initial).firstOrNull { standsOn(player, it) }
}

private fun standsOn(player: ClientPlayerEntity, stance: Stance): Boolean {
	val below = BlockPos(stance.x, stance.y - 1, stance.z)
	val state = player.entityWorld.getBlockState(below)
	return !state.isIn(BlockTags.CLIMBABLE) && !state.getCollisionShape(player.entityWorld, below).isEmpty
}
