package com.lambda.pathing.debug

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.FallLimits
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.store.StoredTerrain
import com.lambda.pathing.world.store.TerrainStore
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object PlanDump {
	const val VERSION = 6

	private const val EMBEDDED_PHYSICS_VERSION = 5

	private const val PLAIN_EXTENSION = ".dump"

	private const val EXTENSION = "$PLAIN_EXTENSION.gz"

	private const val BOX_COUNT_FIELD = 15

	fun readable(version: String?): Boolean = version == "version $VERSION" || version == "version $EMBEDDED_PHYSICS_VERSION"

	class ExecutionTrace(
		val frame: Int,
		val afterInput: Boolean,
		val initial: MovementSimulationState,
		val inputs: List<MovementSimulationInput>,
		val predicted: List<Pair<Int, MovementSimulationState>>,
		val observed: MovementSimulationState?,
		val history: List<Pair<Int, MovementSimulationState>> = emptyList(),
	) {
		companion object {
			fun of(
				plan: TrajectoryPlan,
				frame: Int,
				afterInput: Boolean,
				observed: MovementSimulationState?,
				history: List<Pair<Int, MovementSimulationState>> = emptyList(),
			): ExecutionTrace {
				val frames = plan.frames
				val last = minOf(frames.size - 1, frame + 1)
				return ExecutionTrace(
					frame, afterInput, plan.initialState,
					frames.subList(0, minOf(frames.size, frame + 1)).map { it.input },
					(maxOf(0, frame - PREDICTED_BEFORE)..last).map { it to frames[it].state },
					observed,
					history,
				)
			}
		}
	}

	data class Loaded(
		val bounds: SimulationSnapshotBounds,
		val sections: Map<Long, ImmutableSnapshotSection>,
		val start: Stance,
		val goal: Stance,
		val initialState: MovementSimulationState,
		val profile: PlayerPhysicsProfile,
		val moveOptions: SimpleMoveOptions,
		val searchConfig: MotionConstraints,
		val note: String,
		val execution: ExecutionTrace? = null,
		val obstacles: LongArray = LongArray(0),
	) {
		fun environment(): SnapshotSimulationEnvironment =
			SnapshotSimulationEnvironment(bounds, sections, SnapshotBlockPhysics.AIR).also { it.entityCells(obstacles) }
	}

	fun write(
		directory: Path,
		store: TerrainStore,
		sections: LongArray,
		bounds: SimulationSnapshotBounds,
		start: Stance,
		goal: Stance,
		initialState: MovementSimulationState,
		profile: PlayerPhysicsProfile,
		moveOptions: SimpleMoveOptions,
		searchConfig: MotionConstraints,
		note: String,
		execution: ExecutionTrace? = null,
		obstacles: LongArray = LongArray(0),
	): Path {
		Files.createDirectories(directory)
		val at = System.currentTimeMillis()
		store.reference(at)
		val path = directory.resolve("plan-$at$EXTENSION")
		GZIPOutputStream(Files.newOutputStream(path)).bufferedWriter().use { out ->
			fun line(text: String) = out.append(text).append('\n')
			line("version $VERSION")
			line("note ${note.replace('\n', ' ')}")
			with(bounds) { line("bounds $minX $minY $minZ $maxX $maxY $maxZ") }
			line("start ${start.x} ${start.y} ${start.z}")
			line("goal ${goal.x} ${goal.y} ${goal.z}")
			line(profile.dump())
			line(moveOptions.dump())
			line(searchConfig.dump())
			line(initialState.dump())
			execution?.let { trace ->
				line("executed ${trace.frame} ${trace.afterInput} ${trace.inputs.size}")
				line(trace.initial.dump("executed-state"))
				trace.observed?.let { line(it.dump("observed")) }
				trace.predicted.forEach { (index, state) -> line(state.dump("predicted $index")) }
				trace.history.forEach { (index, state) -> line(state.dump("observed-at $index")) }
				trace.inputs.forEach { line(it.dump()) }
			}
			line("terrain ${directory.toAbsolutePath().relativize(store.directory.toAbsolutePath())} $at")
			line("sections ${sections.size}")
			sections.sorted().forEach { line("s ${ChunkSectionPos.unpackX(it)} ${ChunkSectionPos.unpackY(it)} ${ChunkSectionPos.unpackZ(it)}") }
			obstacles.forEach { line("e ${BlockPos.unpackLongX(it)} ${BlockPos.unpackLongY(it)} ${BlockPos.unpackLongZ(it)}") }
		}
		return path
	}

	fun isDump(path: Path): Boolean = path.fileName.toString().let { it.endsWith(EXTENSION) || it.endsWith(PLAIN_EXTENSION) }

	fun <T> useLines(path: Path, block: (Sequence<String>) -> T): T {
		val input = Files.newInputStream(path).let { if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(it) else it }
		return input.bufferedReader().useLines(block)
	}

	fun read(path: Path): Loaded = useLines(path) { lines -> parse(path, lines.filter { it.isNotBlank() }) }

	private fun parse(path: Path, lines: Sequence<String>): Loaded {
		var bounds: SimulationSnapshotBounds? = null
		var start: Stance? = null
		var goal: Stance? = null
		var profile: PlayerPhysicsProfile? = null
		var moveOptions = SimpleMoveOptions()
		var optionsFallLimits = false
		var searchConfig = MotionConstraints()
		var state: MovementSimulationState? = null
		var note = ""
		var terrain: Path? = null
		var terrainAt = 0L
		var executed: List<String>? = null
		var executedState: MovementSimulationState? = null
		var observed: MovementSimulationState? = null
		val predicted = ArrayList<Pair<Int, MovementSimulationState>>()
		val inputs = ArrayList<MovementSimulationInput>()
		val sectionKeys = LongArrayList()
		val palette = ArrayList<SnapshotBlockPhysics>()
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		val obstacles = LongArrayList()

		for (line in lines) {
			val parts = line.split(' ')
			when (parts[0]) {
				"version" -> require(readable(line)) { "Unsupported plan dump version ${parts[1]}" }
				"note" -> note = line.removePrefix("note ")
				"bounds" -> bounds = SimulationSnapshotBounds(
					parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
					parts[4].toInt(), parts[5].toInt(), parts[6].toInt(),
				)

				"start" -> start = Stance(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
				"goal" -> goal = Stance(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
				"profile" -> profile = readProfile(parts)
				"options" -> {
					moveOptions = readOptions(parts)
					optionsFallLimits = parts.size > FALL_LIMIT_OPTION
				}
				"search" -> searchConfig = readSearchConfig(parts)
				"state" -> state = readState(parts, checkNotNull(profile) { "profile must precede state" })
				"terrain" -> {
					terrain = path.toAbsolutePath().parent.resolve(parts[1]).normalize()
					terrainAt = parts[2].toLong()
				}
				"executed" -> executed = parts
				"executed-state" -> executedState = readState(parts, checkNotNull(profile) { "profile must precede the executed state" })
				"observed" -> observed = readState(parts, checkNotNull(profile) { "profile must precede the observed state" })
				"predicted" -> predicted += parts[1].toInt() to readState(parts.drop(1), checkNotNull(profile) { "profile must precede predicted states" })
				"i" -> inputs += readInput(parts)
				"s" -> sectionKeys.add(ChunkSectionPos.asLong(parts[1].toInt(), parts[2].toInt(), parts[3].toInt()))
				"p" -> palette += readPhysics(parts)
				"c" -> blocks[BlockPos(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())] =
					palette[parts[4].toInt()]
				"e" -> obstacles.add(BlockPos.asLong(parts[1].toInt(), parts[2].toInt(), parts[3].toInt()))
			}
		}

		val box = checkNotNull(bounds) { "dump has no bounds" }
		return Loaded(
			bounds = box,
			sections = terrain?.let { StoredTerrain.load(it, sectionKeys.toLongArray(), terrainAt, box.minY, box.maxY).physics(moveOptions.ignoreHazards) }
				?: ImmutableSnapshotSection.sectionsOf(blocks),
			start = checkNotNull(start) { "dump has no start" },
			goal = checkNotNull(goal) { "dump has no goal" },
			initialState = checkNotNull(state) { "dump has no entry state" },
			profile = checkNotNull(profile) { "dump has no physics profile" },
			moveOptions = if (optionsFallLimits) moveOptions else moveOptions.copy(fallLimits = searchConfig.fallLimits),
			searchConfig = searchConfig,
			note = note,
			execution = executed?.let { parts ->
				ExecutionTrace(parts[1].toInt(), parts[2].toBoolean(), checkNotNull(executedState) { "an executed trace needs its start state" }, inputs, predicted, observed)
			},
			obstacles = obstacles.toLongArray(),
		)
	}

	private const val PREDICTED_BEFORE = 3


	private fun MovementSimulationInput.dump(): String = listOf(
		"i", forward, strafe, jump, sneak, sprint, useItemSlowdown, rotation?.yaw ?: "-", rotation?.pitch ?: "-",
	).joinToString(" ")

	private fun readInput(parts: List<String>) = MovementSimulationInput(
		forward = parts[1].toDouble(),
		strafe = parts[2].toDouble(),
		jump = parts[3].toBoolean(),
		sneak = parts[4].toBoolean(),
		sprint = parts[5].toBoolean(),
		useItemSlowdown = parts[6].toBoolean(),
		rotation = if (parts[7] == "-") null else Rotation(parts[7].toDouble(), parts[8].toDouble()),
	)

	private const val FALL_LIMIT_OPTION = 15

	private const val SEARCH_FALL_LIMITS = 8

	private fun PlayerPhysicsProfile.dump(): String = listOf(
		"profile", movementSpeed, sneakSpeedModifier, gravity, jumpStrength, stepHeight,
		jumpBoostVelocityModifier, slowFalling, width, height, waterMovementEfficiency, dolphinsGrace, canSprint, canDrown,
	).joinToString(" ")

	private fun readProfile(parts: List<String>) = PlayerPhysicsProfile(
		movementSpeed = parts[1].toDouble(),
		sneakSpeedModifier = parts[2].toDouble(),
		gravity = parts[3].toDouble(),
		jumpStrength = parts[4].toDouble(),
		stepHeight = parts[5].toDouble(),
		jumpBoostVelocityModifier = parts[6].toDouble(),
		slowFalling = parts[7].toBoolean(),
		width = parts[8].toDouble(),
		height = parts[9].toDouble(),
		waterMovementEfficiency = parts.getOrNull(10)?.toDouble() ?: 0.0,
		dolphinsGrace = parts.getOrNull(11)?.toBoolean() ?: false,
		canSprint = parts.getOrNull(12)?.toBoolean() ?: true,
		canDrown = parts.getOrNull(13)?.toBoolean() ?: true,
	)

	private fun SimpleMoveOptions.dump(): String = listOf(
		"options", allowDiagonal, allowStepUp, maxWalkOffDepth, allowJumpCandidates,
		maxJumpSpan, maxJumpDrop, allowOffAxisJumps,
		allowClimbing, allowSlimeBounces, maxBounceDrop, maxDropSpan,
		allowDeepDropJumps, allowSwimming, allowDeepFalls,
		fallLimits.acceptedDamage, fallLimits.safeFallDistance, fallLimits.fallDamageMultiplier,
		ignoreHazards, allowDiving, avoidMobs, allowNeoJumps,
	).joinToString(" ")

	private fun readOptions(parts: List<String>) = SimpleMoveOptions(
		allowDiagonal = parts[1].toBoolean(),
		allowStepUp = parts[2].toBoolean(),
		maxWalkOffDepth = parts[3].toInt(),
		allowJumpCandidates = parts[4].toBoolean(),
		maxJumpSpan = parts[5].toInt(),
		maxJumpDrop = parts[6].toInt(),
		allowOffAxisJumps = parts[7].toBoolean(),
		allowClimbing = parts.getOrNull(8)?.toBoolean() ?: false,
		allowSlimeBounces = parts.getOrNull(9)?.toBoolean() ?: false,
		maxBounceDrop = parts.getOrNull(10)?.toInt() ?: 8,
		maxDropSpan = parts.getOrNull(11)?.toInt() ?: 2,
		allowDeepDropJumps = parts.getOrNull(12)?.toBoolean() ?: false,
		allowSwimming = parts.getOrNull(13)?.toBoolean() ?: true,
		allowDeepFalls = parts.getOrNull(14)?.toBoolean() ?: true,
		fallLimits = parts.getOrNull(15)?.let { accepted ->
			val damage = accepted.toIntOrNull() ?: FallLimits.acceptingFallsOf(accepted.toDouble()).acceptedDamage
			FallLimits(damage, parts[16].toDouble(), parts[17].toDouble())
		} ?: FallLimits(),
		ignoreHazards = parts.getOrNull(18)?.toBoolean() ?: false,
		allowDiving = parts.getOrNull(19)?.toBoolean() ?: true,
		avoidMobs = parts.getOrNull(20)?.toBoolean() ?: false,
		allowNeoJumps = parts.getOrNull(21)?.toBoolean() ?: false,
	)

	private fun MotionConstraints.dump(): String = listOf(
		"search", maxFrames, maxYawDegreesPerFrame, goalRadius,
		sprintModes.joinToString(",").ifEmpty { "-" },
		touchArrival, touchRestRadius, maxSafeFallDistance,
		fallLimits.acceptedDamage, fallLimits.safeFallDistance, fallLimits.fallDamageMultiplier, avoidLavaEdges,
	).joinToString(" ")

	private fun readSearchConfig(parts: List<String>) = MotionConstraints(
		maxFrames = parts[1].toInt(),
		maxYawDegreesPerFrame = parts[2].toDouble(),
		goalRadius = parts[3].toDouble(),
		sprintModes = if (parts[4] == "-") emptyList() else parts[4].split(',').map { it.toBoolean() },
		touchArrival = parts.getOrNull(5)?.toBoolean() ?: false,
		touchRestRadius = parts.getOrNull(6)?.toDouble() ?: 1.6,
		fallLimits = if (parts.size > SEARCH_FALL_LIMITS + 2) {
			FallLimits(parts[SEARCH_FALL_LIMITS].toInt(), parts[SEARCH_FALL_LIMITS + 1].toDouble(), parts[SEARCH_FALL_LIMITS + 2].toDouble())
		} else {
			FallLimits.acceptingFallsOf(parts.getOrNull(7)?.toDouble() ?: FallLimits.DEFAULT_SAFE_FALL_DISTANCE)
		},
		avoidLavaEdges = parts.getOrNull(SEARCH_FALL_LIMITS + 3)?.let { it == "true" || (it.toDoubleOrNull() ?: 0.0) > 0.0 } ?: false,
	)

	private fun MovementSimulationState.dump(keyword: String = "state"): String = listOf(
		keyword, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z,
		rotation.yaw, rotation.pitch, onGround, isJumping, isSprinting, isSneaking,
		jumpingCooldown, horizontalCollision, collidedSoftly, verticalCollision,
		velocityAffectingPos.x, velocityAffectingPos.y, velocityAffectingPos.z,
		supportingBlockPos?.let { "${it.x} ${it.y} ${it.z}" } ?: "none",
		touchingWater, waterHeight, eyeInWater, submergedInWater, swimming, air,
	).joinToString(" ")

	private fun readState(parts: List<String>, profile: PlayerPhysicsProfile): MovementSimulationState {
		val position = Vec3d(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
		val water = parts.drop(if (parts[20] == "none") 21 else 23)
		return MovementSimulationState.synthetic(
			profile = profile,
			position = position,
			rotation = Rotation(parts[7].toDouble(), parts[8].toDouble()),
			velocity = Vec3d(parts[4].toDouble(), parts[5].toDouble(), parts[6].toDouble()),
			onGround = parts[9].toBoolean(),
		).copy(
			isJumping = parts[10].toBoolean(),
			isSprinting = parts[11].toBoolean(),
			isSneaking = parts[12].toBoolean(),
			jumpingCooldown = parts[13].toInt(),
			horizontalCollision = parts[14].toBoolean(),
			collidedSoftly = parts[15].toBoolean(),
			verticalCollision = parts[16].toBoolean(),
			velocityAffectingPos = BlockPos(parts[17].toInt(), parts[18].toInt(), parts[19].toInt()),
			supportingBlockPos = if (parts[20] == "none") null else {
				BlockPos(parts[20].toInt(), parts[21].toInt(), parts[22].toInt())
			},
			touchingWater = water.getOrNull(0)?.toBoolean() ?: false,
			waterHeight = water.getOrNull(1)?.toDouble() ?: 0.0,
			eyeInWater = water.getOrNull(2)?.toBoolean() ?: false,
			submergedInWater = water.getOrNull(3)?.toBoolean() ?: false,
			swimming = water.getOrNull(4)?.toBoolean() ?: false,
			air = water.getOrNull(5)?.toInt() ?: Vanilla.MAX_AIR,
		)
	}

	private fun readPhysics(parts: List<String>): SnapshotBlockPhysics {
		val boxCount = parts[BOX_COUNT_FIELD].toInt()
		var shape = VoxelShapes.empty()
		for (box in 0 until boxCount) {
			val base = BOX_COUNT_FIELD + 1 + box * 6
			shape = VoxelShapes.union(
				shape,
				VoxelShapes.cuboid(
					Box(
						parts[base].toDouble(), parts[base + 1].toDouble(), parts[base + 2].toDouble(),
						parts[base + 3].toDouble(), parts[base + 4].toDouble(), parts[base + 5].toDouble(),
					)
				),
			)
		}
		var water: WaterPhysics? = null
		var exceedsCube: Boolean? = null
		var tail = BOX_COUNT_FIELD + 1 + boxCount * 6
		while (tail < parts.size) when (parts[tail]) {
			"water" -> {
				water = WaterPhysics(parts[tail + 1].toFloat(), Vec3d(parts[tail + 2].toDouble(), parts[tail + 3].toDouble(), parts[tail + 4].toDouble()))
				tail += 5
			}
			"exceeds" -> {
				exceedsCube = parts[tail + 1].toBoolean()
				tail += 2
			}
			else -> tail++
		}
		val medium = Medium.valueOf(parts[10])
		val unsupportedKind = parts[11].takeIf { it != "-" }?.let { UnsupportedPhysicsKind.valueOf(it) }
		return SnapshotBlockPhysics(
			collisionShape = shape,
			slipperiness = parts[1].toDouble(),
			velocityMultiplier = parts[2].toDouble(),
			jumpVelocityMultiplier = parts[3].toDouble(),
			unsupportedPhysics = unsupportedKind?.let {
				UnsupportedPhysics(it, parts[12].takeIf { id -> id != "-" })
			},
			coarseVoxel = CoarseVoxel(
				fullyPassable = parts[5].toBoolean(),
				centerPassable = parts[6].toBoolean(),
				standingSurface = parts[7].toDouble().takeIf { it >= 0.0 },
				intrusionHeight = parts[8].toDouble(),
				bouncy = parts[9].toBoolean(),
				medium = medium,
			),
			fenceLike = parts[4].toBoolean(),
			bounceFactor = parts[13].toDouble(),
			dampensSteppingSpeed = parts[14].toBoolean(),
			water = water,
		).let { physics -> exceedsCube?.let { physics.copy(exceedsCube = it) } ?: physics }
	}
}
