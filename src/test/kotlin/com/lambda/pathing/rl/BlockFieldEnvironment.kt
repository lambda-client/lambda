/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural three-dimensional movement curriculum.
 *
 * A course is generated in two layers. First, a conservative traversable
 * backbone is built from platforms, turns, one-block height changes and
 * one-to-three-block gaps. Random surrounding columns and obstacles are then
 * added at a sampled density. Start and goal are sampled far apart on the
 * backbone and the direction is randomized.
 *
 * The backbone is not sent to the policy verbatim. It only receives three
 * sparse look-ahead anchors, analogous to guidance from the planner's lazy
 * graph, plus a local 3-D occupancy volume. Exact [MovementSimulator] physics
 * remains authoritative.
 *
 * Difficulty is a table of independent terrain axes ([LEVELS]) rather than one
 * scalar knob. Each level moves as few axes as possible so that a policy which
 * masters a level carries most of it forward; the previous schedule changed
 * four axes per step and nothing transferred. Every level contains at least one
 * gap, because gap crossing is the skill the planner needs and a gap-free base
 * level trains a controller that never learns to leave the ground.
 */
class BlockFieldEnvironment : RlEnvironment {
    private data class RoutePoint(val x: Int, val floorY: Int, val z: Int) {
        fun feetPosition(): Vec3d = Vec3d(x + 0.5, floorY + 1.0, z + 0.5)
    }

    private data class Course(
        val blocks: Set<BlockPos>,
        val route: List<RoutePoint>,
        val density: Float,
        val difficulty: Int,
    )

    /** One curriculum step. Fields are the independent axes of course shape. */
    private data class Level(
        val gapCount: Int,
        val maxGapWidth: Int,
        val heightChanges: Boolean,
        val routeHalfWidth: Int,
        val obstacleRate: Double,
        val minDensity: Double,
        val maxDensity: Double,
    )

    private lateinit var simulator: MovementSimulator
    private lateinit var blocks: Set<BlockPos>
    private lateinit var taskRoute: List<RoutePoint>
    private lateinit var target: Vec3d
    private var desiredVelocity = Vec3d.ZERO
    private var density = 1.0f
    private var difficulty = 0
    private var ticks = 0
    private var routeCursor = 0
    private var previousProgressPotential = 0.0
    private var previousTerminalPotential = 0.0
    private var bestProgressPotential = 0.0
    private var ticksSinceProgress = 0

    override fun reset(seed: Long, maxDifficulty: Int): RlTransition {
        val random = SplittableRandom(seed)
        val course = generateCourse(random, maxDifficulty)
        blocks = course.blocks
        density = course.density
        difficulty = course.difficulty

        val lowEndpoint = random.nextInt(START_INDEX_MIN, START_INDEX_MAX + 1)
        val highEndpoint = random.nextInt(GOAL_INDEX_MIN, GOAL_INDEX_MAX + 1)
        val forward = random.nextBoolean()
        taskRoute = if (forward) {
            course.route.subList(lowEndpoint, highEndpoint + 1)
        } else {
            course.route.subList(lowEndpoint, highEndpoint + 1).asReversed()
        }

        val start = taskRoute.first().feetPosition()
        val goalPoint = taskRoute.last()
        target = goalPoint.feetPosition().add(
            random.nextDouble(-0.22, 0.22),
            0.0,
            random.nextDouble(-0.22, 0.22),
        )

        val terminalDirection = horizontalDirection(
            taskRoute[taskRoute.lastIndex - 1].feetPosition(),
            taskRoute.last().feetPosition(),
        )
        desiredVelocity = if (random.nextDouble() < STOP_GOAL_FRACTION) {
            Vec3d.ZERO
        } else {
            val speed = random.nextDouble(MIN_TERMINAL_SPEED, MAX_TERMINAL_SPEED)
            Vec3d(terminalDirection.x * speed, 0.0, terminalDirection.z * speed)
        }

        val startDirection = horizontalDirection(
            taskRoute.first().feetPosition(),
            taskRoute[1].feetPosition(),
        )
        val startYaw = minecraftYaw(startDirection) + random.nextDouble(-38.0, 38.0)
        // The planner splices from moving tape frames far more often than it
        // bootstraps from rest, so entry speed is sampled across the whole
        // walk-to-sprint range at every level. Gating fast entries behind the
        // hardest levels left the policy blind to exactly the states it is
        // queried from.
        val startsFromRest = random.nextDouble() < REST_START_FRACTION
        val initialSpeed = if (startsFromRest) 0.0 else random.nextDouble(0.0, MAX_ENTRY_SPEED)
        val initialVelocity = Vec3d(
            startDirection.x * initialSpeed + random.nextDouble(-0.025, 0.025),
            0.0,
            startDirection.z * initialSpeed + random.nextDouble(-0.025, 0.025),
        )
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = BOUNDS,
            blocks = blocks.associateWith { SnapshotBlockPhysics.FULL_CUBE },
        )
        simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = start,
                rotation = Rotation(startYaw, 0.0),
                velocity = initialVelocity,
                // A body already at sprint speed is a sprinting body; starting it
                // unsprinted would decay the entry state the planner handed over.
                isSprinting = initialSpeed >= SPRINT_ENTRY_SPEED,
            ),
            skipEntityCollisions = true,
        )

        ticks = 0
        routeCursor = 0
        previousProgressPotential = progressPotential(start)
        previousTerminalPotential = terminalPotential(start, initialVelocity)
        bestProgressPotential = previousProgressPotential
        ticksSinceProgress = 0
        return transition(0.0f, RlOutcome.RUNNING)
    }

    override fun step(action: IntArray): RlTransition {
        require(action.size == ACTION_DIMS.size) {
            "Expected ${ACTION_DIMS.size} action components, got ${action.size}"
        }
        action.forEachIndexed { index, value ->
            require(value in 0 until ACTION_DIMS[index]) {
                "Action component $index=$value is outside 0 until ${ACTION_DIMS[index]}"
            }
        }

        val beforeProgress = previousProgressPotential
        val beforeTerminal = previousTerminalPotential
        val state = simulator.state
        val input = MovementSimulationInput(
            forward = AXIS_ACTIONS[action[0]],
            strafe = AXIS_ACTIONS[action[1]],
            jump = action[2] == 1,
            sprint = action[3] == 1,
            rotation = Rotation(state.rotation.yaw + YAW_DELTAS[action[4]], 0.0),
        )
        val result = simulator.tryTickMovement(input)
        ticks++
        if (result is MovementSimulationStepResult.Rejected) {
            return transition(FAILURE_REWARD, RlOutcome.SIMULATION_REJECTED)
        }

        val after = simulator.state
        advanceRouteCursor(after.position)
        previousProgressPotential = progressPotential(after.position)
        previousTerminalPotential = terminalPotential(after.position, after.velocity)

        if (previousProgressPotential > bestProgressPotential + PROGRESS_EPSILON) {
            bestProgressPotential = previousProgressPotential
            ticksSinceProgress = 0
        } else {
            ticksSinceProgress++
        }

        var reward = (
            (previousProgressPotential - beforeProgress) * PROGRESS_REWARD +
                (previousTerminalPotential - beforeTerminal) * TERMINAL_SHAPING_REWARD
            ).toFloat() + STEP_REWARD
        if (input.jump) reward += JUMP_PRESS_REWARD
        if (after.horizontalCollision) reward += COLLISION_REWARD

        val distance = distanceToGoal(after.position)
        val velocityError = horizontalVelocityError(after.velocity)
        val outcome = when {
            after.position.y < FALL_Y -> RlOutcome.FALL
            after.position.x !in COURSE_MIN_X..COURSE_MAX_X ||
                after.position.z !in COURSE_MIN_Z..COURSE_MAX_Z ||
                after.position.y > COURSE_MAX_Y -> RlOutcome.OUT_OF_BOUNDS
            after.onGround &&
                distance <= GOAL_RADIUS &&
                velocityError <= terminalVelocityTolerance() -> RlOutcome.SUCCESS
            // Parking in front of a hazard used to cost only the accumulated step
            // reward while attempting it risked the full failure reward, which made
            // standing still an order of magnitude cheaper than trying. Refusing to
            // move is now scored exactly like falling, so any attempt with a nonzero
            // success chance strictly dominates it. Exempt the terminal approach,
            // where braking onto a stop goal legitimately makes no route progress.
            ticksSinceProgress >= STALL_TICKS &&
                distance > STALL_EXEMPT_RADIUS -> RlOutcome.STALLED
            ticks >= MAX_TICKS -> RlOutcome.TIME_LIMIT
            else -> RlOutcome.RUNNING
        }
        reward += when (outcome) {
            RlOutcome.SUCCESS -> SUCCESS_REWARD
            RlOutcome.FALL,
            RlOutcome.OUT_OF_BOUNDS,
            RlOutcome.SIMULATION_REJECTED,
            RlOutcome.STALLED -> FAILURE_REWARD
            RlOutcome.RUNNING, RlOutcome.TIME_LIMIT -> 0.0f
        }
        return transition(reward, outcome)
    }

    private fun transition(reward: Float, outcome: RlOutcome): RlTransition =
        RlTransition(
            observation = observation(),
            reward = reward,
            terminated = outcome != RlOutcome.RUNNING && outcome != RlOutcome.TIME_LIMIT,
            truncated = outcome == RlOutcome.TIME_LIMIT,
            outcome = outcome,
            ticks = ticks,
            scenario = difficulty,
            distanceToGoal = distanceToGoal(simulator.state.position).toFloat(),
            density = density,
        )

    private fun observation(): FloatArray {
        val state = simulator.state
        val position = state.position
        val result = FloatArray(OBSERVATION_SIZE)
        var i = 0

        result[i++] = ((target.x - position.x) / FIELD_LENGTH).toFloat()
        result[i++] = ((target.y - position.y) / 8.0).toFloat()
        result[i++] = ((target.z - position.z) / FIELD_WIDTH).toFloat()
        result[i++] = (state.velocity.x / 0.6).toFloat()
        result[i++] = (state.velocity.y / 0.6).toFloat()
        result[i++] = (state.velocity.z / 0.6).toFloat()
        val yawRadians = state.rotation.yaw * PI / 180.0
        result[i++] = sin(yawRadians).toFloat()
        result[i++] = cos(yawRadians).toFloat()
        result[i++] = if (state.onGround) 1.0f else 0.0f
        result[i++] = if (state.isSprinting) 1.0f else 0.0f
        result[i++] = state.jumpingCooldown / 10.0f
        result[i++] = if (state.horizontalCollision) 1.0f else 0.0f
        result[i++] = if (state.verticalCollision) 1.0f else 0.0f
        result[i++] = (ticks.toDouble() / MAX_TICKS).toFloat()
        result[i++] = ((taskRoute.lastIndex - routeCursor).toDouble() / taskRoute.size).toFloat()
        result[i++] = (desiredVelocity.x / 0.4).toFloat()
        result[i++] = (desiredVelocity.z / 0.4).toFloat()
        result[i++] = density

        for (offset in ANCHOR_OFFSETS) {
            val anchor = taskRoute[(routeCursor + offset).coerceAtMost(taskRoute.lastIndex)]
                .feetPosition()
            result[i++] = ((anchor.x - position.x) / 12.0).toFloat()
            result[i++] = ((anchor.y - position.y) / 6.0).toFloat()
            result[i++] = ((anchor.z - position.z) / 12.0).toFloat()
        }
        check(i == DENSE_OBSERVATIONS)

        val centerX = floor(position.x).toInt()
        val centerY = floor(position.y).toInt()
        val centerZ = floor(position.z).toInt()
        for (dy in VOXEL_MIN_DY..VOXEL_MAX_DY) {
            for (dx in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                for (dz in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                    result[i++] = if (BlockPos(centerX + dx, centerY + dy, centerZ + dz) in blocks) {
                        1.0f
                    } else {
                        0.0f
                    }
                }
            }
        }
        check(i == result.size)
        return result
    }

    private fun advanceRouteCursor(position: Vec3d) {
        val searchEnd = (routeCursor + 8).coerceAtMost(taskRoute.lastIndex)
        var bestIndex = routeCursor
        var bestDistance = routePointDistanceSquared(position, taskRoute[routeCursor])
        for (index in routeCursor + 1..searchEnd) {
            val distance = routePointDistanceSquared(position, taskRoute[index])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        routeCursor = bestIndex
    }

    private fun progressPotential(position: Vec3d): Double {
        val point = taskRoute[routeCursor].feetPosition()
        val next = taskRoute[(routeCursor + 1).coerceAtMost(taskRoute.lastIndex)].feetPosition()
        val segmentX = next.x - point.x
        val segmentY = next.y - point.y
        val segmentZ = next.z - point.z
        val lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ
        val fraction = if (lengthSquared <= 1.0e-9) {
            0.0
        } else {
            (
                (position.x - point.x) * segmentX +
                    (position.y - point.y) * segmentY +
                    (position.z - point.z) * segmentZ
                ) / lengthSquared
        }.coerceIn(0.0, 1.0)
        return routeCursor + fraction
    }

    private fun terminalPotential(position: Vec3d, velocity: Vec3d): Double =
        -distanceToGoal(position) - horizontalVelocityError(velocity) * 1.5

    private fun distanceToGoal(position: Vec3d): Double {
        val dx = target.x - position.x
        val dy = target.y - position.y
        val dz = target.z - position.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun horizontalVelocityError(velocity: Vec3d): Double =
        hypot(velocity.x - desiredVelocity.x, velocity.z - desiredVelocity.z)

    private fun terminalVelocityTolerance(): Double =
        if (
            desiredVelocity.x * desiredVelocity.x + desiredVelocity.z * desiredVelocity.z < 0.0025
        ) STOP_VELOCITY_TOLERANCE
        else MOVING_VELOCITY_TOLERANCE

    private fun generateCourse(random: SplittableRandom, maxDifficulty: Int): Course {
        val difficulty = sampleDifficulty(random, maxDifficulty)
        val level = LEVELS[difficulty]
        val requestedDensity = random.nextDouble(level.minDensity, level.maxDensity)
        val gaps = generateGaps(random, level)
        val route = generateRoute(random, level, gaps)
        val heights = Array(FIELD_LENGTH) { IntArray(FIELD_WIDTH) { AIR_COLUMN } }

        for (x in 0 until FIELD_LENGTH) {
            for (zIndex in 0 until FIELD_WIDTH) {
                if (random.nextDouble() > requestedDensity) continue
                val z = zIndex + FIELD_MIN_Z
                val neighborHeight = when {
                    x > 0 && heights[x - 1][zIndex] != AIR_COLUMN -> heights[x - 1][zIndex]
                    zIndex > 0 && heights[x][zIndex - 1] != AIR_COLUMN -> heights[x][zIndex - 1]
                    else -> random.nextInt(-1, 4)
                }
                val change = when (random.nextInt(10)) {
                    0 -> -1
                    1 -> 1
                    else -> 0
                }
                heights[x][zIndex] = (neighborHeight + change).coerceIn(MIN_FLOOR_Y, MAX_FLOOR_Y)
                if (abs(z) == FIELD_MAX_Z) heights[x][zIndex] = MIN_FLOOR_Y
            }
        }

        val routeHalfWidth = level.routeHalfWidth
        route.forEachIndexed { x, point ->
            val gap = gaps.any { x in it }
            for (z in point.z - routeHalfWidth..point.z + routeHalfWidth) {
                if (z !in FIELD_MIN_Z..FIELD_MAX_Z) continue
                heights[x][z - FIELD_MIN_Z] = if (gap) AIR_COLUMN else point.floorY
            }
        }

        val mutableBlocks = HashSet<BlockPos>()
        for (x in 0 until FIELD_LENGTH) {
            for (zIndex in 0 until FIELD_WIDTH) {
                val top = heights[x][zIndex]
                if (top == AIR_COLUMN) continue
                val z = zIndex + FIELD_MIN_Z
                for (y in BOTTOM_Y..top) {
                    mutableBlocks += BlockPos(x, y, z)
                }

                val routePoint = route[x]
                val protected = abs(z - routePoint.z) <= routeHalfWidth + 1
                if (!protected && random.nextDouble() < level.obstacleRate) {
                    val obstacleHeight = random.nextInt(1, MAX_OBSTACLE_HEIGHT + 1)
                    for (dy in 1..obstacleHeight) {
                        mutableBlocks += BlockPos(x, top + dy, z)
                    }
                }
            }
        }

        // Maintain two blocks of body clearance around the certified route.
        route.forEachIndexed { x, point ->
            val gap = gaps.any { x in it }
            for (z in point.z - routeHalfWidth..point.z + routeHalfWidth) {
                for (y in point.floorY + 1..point.floorY + 3) {
                    mutableBlocks.remove(BlockPos(x, y, z))
                }
                if (gap) {
                    for (y in BOTTOM_Y..point.floorY) {
                        mutableBlocks.remove(BlockPos(x, y, z))
                    }
                }
            }
        }

        val occupiedColumns = heights.sumOf { row -> row.count { it != AIR_COLUMN } }
        val actualDensity = occupiedColumns.toFloat() / (FIELD_LENGTH * FIELD_WIDTH)
        return Course(mutableBlocks, route, actualDensity, difficulty)
    }

    /**
     * Curriculum levels emphasise their ceiling so that the newly unlocked axis
     * actually gets trained; uniform sampling over `0..ceiling` spent most of the
     * budget re-proving levels the policy had already mastered. Evaluation passes
     * [UNIFORM_DIFFICULTY] to get an unweighted sweep of every level instead.
     */
    private fun sampleDifficulty(random: SplittableRandom, maxDifficulty: Int): Int {
        if (maxDifficulty >= UNIFORM_DIFFICULTY) return random.nextInt(LEVELS.size)
        val ceiling = maxDifficulty.coerceIn(0, LEVELS.lastIndex)
        return if (ceiling > 0 && random.nextDouble() < CEILING_SAMPLE_FRACTION) ceiling
        else random.nextInt(ceiling + 1)
    }

    private fun generateGaps(random: SplittableRandom, level: Level): List<IntRange> {
        if (level.gapCount == 0) return emptyList()
        val result = ArrayList<IntRange>()
        var attempts = 0
        while (result.size < level.gapCount && attempts++ < 100) {
            val width = random.nextInt(1, level.maxGapWidth + 1)
            val start = random.nextInt(GAP_MIN_X, GAP_MAX_X - width + 2)
            val candidate = start until start + width
            if (result.none { existing ->
                    candidate.first <= existing.last + GAP_SEPARATION &&
                        candidate.last >= existing.first - GAP_SEPARATION
                }
            ) {
                result += candidate
            }
        }
        return result.sortedBy(IntRange::first)
    }

    private fun generateRoute(
        random: SplittableRandom,
        level: Level,
        gaps: List<IntRange>,
    ): List<RoutePoint> {
        val result = ArrayList<RoutePoint>(FIELD_LENGTH)
        var z = random.nextInt(-3, 4)
        var floorY = random.nextInt(0, 3)
        var turnCooldown = random.nextInt(3, 7)
        var heightCooldown = random.nextInt(6, 11)

        for (x in 0 until FIELD_LENGTH) {
            val protectedGapApproach = gaps.any {
                x in (it.first - GAP_APPROACH)..(it.last + GAP_LANDING)
            }
            if (!protectedGapApproach && x > START_INDEX_MAX && x < GOAL_INDEX_MIN) {
                if (--turnCooldown <= 0) {
                    z = (z + random.nextInt(-1, 2)).coerceIn(ROUTE_MIN_Z, ROUTE_MAX_Z)
                    turnCooldown = random.nextInt(3, 7)
                }
                if (level.heightChanges && --heightCooldown <= 0) {
                    val delta = if (floorY <= MIN_ROUTE_Y) 1
                    else if (floorY >= MAX_ROUTE_Y) -1
                    else if (random.nextBoolean()) 1 else -1
                    floorY += delta
                    heightCooldown = random.nextInt(7, 13)
                }
            }
            result += RoutePoint(x, floorY, z)
        }
        return result
    }

    private fun routePointDistanceSquared(position: Vec3d, point: RoutePoint): Double {
        val feet = point.feetPosition()
        val dx = position.x - feet.x
        val dy = position.y - feet.y
        val dz = position.z - feet.z
        return dx * dx + dy * dy * 2.0 + dz * dz
    }

    private fun horizontalDirection(from: Vec3d, to: Vec3d): Vec3d {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val length = hypot(dx, dz)
        return if (length <= 1.0e-9) Vec3d(1.0, 0.0, 0.0)
        else Vec3d(dx / length, 0.0, dz / length)
    }

    private fun minecraftYaw(direction: Vec3d): Double =
        atan2(direction.z, direction.x) * 180.0 / PI - 90.0

    companion object {
        val ACTION_DIMS = intArrayOf(3, 3, 2, 2, 7)

        const val DENSE_OBSERVATIONS = 27
        const val VOXEL_HALF_XZ = 7
        const val VOXEL_MIN_DY = -3
        const val VOXEL_MAX_DY = 5
        const val VOXEL_SIZE_XZ = VOXEL_HALF_XZ * 2 + 1
        const val VOXEL_SIZE_Y = VOXEL_MAX_DY - VOXEL_MIN_DY + 1
        const val OBSERVATION_SIZE =
            DENSE_OBSERVATIONS + VOXEL_SIZE_Y * VOXEL_SIZE_XZ * VOXEL_SIZE_XZ
        const val MAX_TICKS = 320

        private const val FIELD_LENGTH = 48
        private const val FIELD_WIDTH = 25
        private const val FIELD_MIN_Z = -12
        private const val FIELD_MAX_Z = 12
        private const val BOTTOM_Y = -4
        private const val MIN_FLOOR_Y = -1
        private const val MAX_FLOOR_Y = 5
        private const val MIN_ROUTE_Y = 0
        private const val MAX_ROUTE_Y = 4
        private const val AIR_COLUMN = Int.MIN_VALUE
        private const val START_INDEX_MIN = 2
        private const val START_INDEX_MAX = 7
        private const val GOAL_INDEX_MIN = 39
        private const val GOAL_INDEX_MAX = 45
        private const val GAP_MIN_X = 12
        private const val GAP_MAX_X = 35
        private const val GAP_SEPARATION = 7
        private const val GAP_APPROACH = 4
        private const val GAP_LANDING = 3
        private const val ROUTE_MIN_Z = -7
        private const val ROUTE_MAX_Z = 7
        private const val MAX_OBSTACLE_HEIGHT = 3

        /** Wire value asking for an unweighted sweep of every level (evaluation). */
        const val UNIFORM_DIFFICULTY = 255
        private const val CEILING_SAMPLE_FRACTION = 0.5

        val DIFFICULTY_LEVELS: Int get() = LEVELS.size

        /** Exposed so tests can assert the curriculum's shape without resetting. */
        fun gapCountForLevel(level: Int): Int = LEVELS[level].gapCount

        private const val FALL_Y = -5.5
        private const val COURSE_MIN_X = -2.0
        private const val COURSE_MAX_X = 50.0
        private const val COURSE_MIN_Z = -13.5
        private const val COURSE_MAX_Z = 13.5
        private const val COURSE_MAX_Y = 12.0
        private const val GOAL_RADIUS = 0.9
        private const val STOP_VELOCITY_TOLERANCE = 0.115
        private const val MOVING_VELOCITY_TOLERANCE = 0.20
        private const val STOP_GOAL_FRACTION = 0.5
        private const val MIN_TERMINAL_SPEED = 0.18
        private const val MAX_TERMINAL_SPEED = 0.29
        private const val REST_START_FRACTION = 0.25
        private const val MAX_ENTRY_SPEED = 0.28
        private const val SPRINT_ENTRY_SPEED = 0.16

        /** Consecutive ticks without new route progress before the episode dies. */
        private const val STALL_TICKS = 60
        private const val STALL_EXEMPT_RADIUS = 3.0
        private const val PROGRESS_EPSILON = 1.0e-3

        private const val PROGRESS_REWARD = 0.10
        private const val TERMINAL_SHAPING_REWARD = 0.08
        private const val STEP_REWARD = -0.003f
        private const val COLLISION_REWARD = -0.025f
        private const val JUMP_PRESS_REWARD = -0.0005f
        private const val SUCCESS_REWARD = 10.0f
        private const val FAILURE_REWARD = -10.0f

        private val ANCHOR_OFFSETS = intArrayOf(3, 7, 11)

        /**
         * The curriculum, one axis at a time. Level 0 already contains a
         * one-block gap on a flat wide corridor, so jumping is trained from the
         * first episode; each later level introduces a single new demand:
         *
         * ```text
         * 0 -> baseline: one narrow gap, flat, wide corridor, dense surroundings
         * 1 -> + one-block height changes along the route
         * 2 -> + wider gaps (up to two blocks)
         * 3 -> + sparse surroundings and obstacles beside the corridor
         * 4 -> + a narrow three-wide corridor
         * 5 -> + three gaps up to three blocks wide
         * ```
         */
        private val LEVELS = arrayOf(
            Level(1, 1, false, 2, 0.000, 0.88, 0.98),
            Level(1, 1, true, 2, 0.000, 0.88, 0.98),
            Level(1, 2, true, 2, 0.000, 0.85, 0.96),
            Level(2, 2, true, 2, 0.020, 0.62, 0.85),
            Level(2, 2, true, 1, 0.035, 0.55, 0.80),
            Level(3, 3, true, 1, 0.050, 0.40, 0.68),
        )
        private val AXIS_ACTIONS = doubleArrayOf(-1.0, 0.0, 1.0)
        private val YAW_DELTAS = doubleArrayOf(-15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0)
        private val BOUNDS = SimulationSnapshotBounds(-4, -8, -18, 54, 16, 18)
        private val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1,
            sneakSpeedModifier = 0.3,
            gravity = 0.08,
            jumpStrength = 0.42,
            stepHeight = 0.6,
            jumpBoostVelocityModifier = 0.0,
            slowFalling = false,
            width = 0.6,
            height = 1.8,
            eyeHeight = 1.62,
        )
    }
}
