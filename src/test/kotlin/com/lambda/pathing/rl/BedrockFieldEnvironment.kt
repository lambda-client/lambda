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
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.core.TailCost
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.trajectory.tailLowerBound
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * The transfer test and quality bench for a policy trained on [BlockFieldEnvironment].
 *
 * Same terrain, endpoint pairs and coarse planner as `./gradlew bedrockCorpus`, so a
 * learned movement policy and the non-learned [com.lambda.pathing.trajectory.WalkingSeedSearch]
 * are finally measured on one field. The look-ahead anchors come from the real D* Lite
 * route; the exact simulator remains the authority.
 *
 * The observation is byte-compatible with [BlockFieldEnvironment] so a trained
 * checkpoint runs here unchanged.
 *
 * Two modes:
 *
 * - **Whole route** ([segmentMode] false): traverse an entire corpus route with a
 *   rolling near goal. Pessimistic end-to-end; pays the per-jump exponent over a long
 *   jump chain.
 * - **Segment chain** ([segmentMode] true): a chain of [CHAIN_EDGES] coarse edges from
 *   a grounded start stance to a grounded goal stance, both endpoints real coarse
 *   nodes, entered with a moving hand-off and braked to a stop at the end. The
 *   per-seed setup ([segmentSetup]) is shared with the head-to-head quality bench so
 *   the policy and the search see byte-identical start states and endpoints.
 */
class BedrockFieldEnvironment(
    private val segmentMode: Boolean = false,
) : RlEnvironment {
    private lateinit var simulator: MovementSimulator
    private lateinit var routeNodes: List<Vec3d>
    /** Coarse plan whose tail cost is this episode's value function (chain goal in segment mode). */
    private lateinit var valuePlan: CoarseRoutePlan
    /** Densified-index of each [valuePlan] node, for mapping the cursor to a coarse node. */
    private lateinit var valueCoarseIndices: IntArray
    private var startValue = 1.0
    private var previousValue = 1.0
    private var caseIndex = 0
    private var scenarioTag = 0
    private var movingEntry = false
    private var maxTicks = DEFAULT_MAX_TICKS
    private var ticks = 0
    private var routeCursor = 0
    private var target = Vec3d.ZERO
    private var desiredVelocity = Vec3d.ZERO
    private var previousProgressPotential = 0.0
    private var previousTerminalPotential = 0.0
    private var bestProgressPotential = 0.0
    private var ticksSinceProgress = 0
    private var arrivalLatch = 0

    override fun reset(seed: Long, maxDifficulty: Int): RlTransition {
        if (segmentMode) {
            val setup = segmentSetup(seed)
            caseIndex = setup.caseIndex
            scenarioTag = setup.jumpCount
            routeNodes = setup.anchorPoints
            target = setup.target
            desiredVelocity = setup.desiredVelocity
            valuePlan = setup.valuePlan
            valueCoarseIndices = setup.valueCoarseIndices
            movingEntry = true
            simulator = MovementSimulator(
                profile = PROFILE,
                environment = ENVIRONMENT,
                initialState = setup.startState,
                skipEntityCollisions = true,
            )
            maxTicks = CHAIN_MAX_TICKS
        } else {
            val random = SplittableRandom(seed)
            val case = CASES[random.nextInt(CASES.size).also { caseIndex = it }]
            routeNodes = case.points
            target = routeNodes.last()
            desiredVelocity = Vec3d.ZERO
            valuePlan = case.plan
            valueCoarseIndices = case.coarseIndices
            scenarioTag = caseIndex
            val start = routeNodes.first()
            val startDirection = horizontalDirection(start, routeNodes[1.coerceAtMost(routeNodes.lastIndex)])
            val startYaw = minecraftYaw(startDirection) + random.nextDouble(-20.0, 20.0)
            val speed = if (random.nextDouble() < REST_START_FRACTION) 0.0 else random.nextDouble(0.0, MAX_ENTRY_SPEED)
            movingEntry = speed > 0.0
            simulator = MovementSimulator(
                profile = PROFILE,
                environment = ENVIRONMENT,
                initialState = MovementSimulationState.synthetic(
                    profile = PROFILE,
                    position = start,
                    rotation = Rotation(startYaw, 0.0),
                    velocity = Vec3d(startDirection.x * speed, 0.0, startDirection.z * speed),
                    isSprinting = speed >= SPRINT_ENTRY_SPEED,
                ),
                skipEntityCollisions = true,
            )
            maxTicks = (routeNodes.size * TICKS_PER_ROUTE_NODE).coerceAtLeast(DEFAULT_MAX_TICKS)
        }

        ticks = 0
        routeCursor = 0
        previousProgressPotential = progressPotential(simulator.state.position)
        previousTerminalPotential = terminalPotential(simulator.state.position, simulator.state.velocity)
        bestProgressPotential = previousProgressPotential
        ticksSinceProgress = 0
        arrivalLatch = 0
        startValue = valueAt(simulator.state).coerceAtLeast(1.0)
        previousValue = startValue
        return transition(0.0f, RlOutcome.RUNNING)
    }

    override fun step(action: IntArray): RlTransition {
        require(action.size == ACTION_DIMS.size) {
            "Expected ${ACTION_DIMS.size} action components, got ${action.size}"
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
        previousValue = valueAt(after)
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
        if (after.horizontalCollision) reward += COLLISION_REWARD

        val distance = distanceToGoal(after.position)
        val outcome = when {
            after.position.y < FALL_Y -> RlOutcome.FALL
            after.position.x !in COURSE_MIN_X..COURSE_MAX_X ||
                after.position.z !in COURSE_MIN_Z..COURSE_MAX_Z ||
                after.position.y > COURSE_MAX_Y -> RlOutcome.OUT_OF_BOUNDS
            reachedGoal(after, distance) -> RlOutcome.SUCCESS
            ticksSinceProgress >= STALL_TICKS && distance > STALL_EXEMPT_RADIUS -> RlOutcome.STALLED
            ticks >= maxTicks -> RlOutcome.TIME_LIMIT
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
            scenario = scenarioTag,
            distanceToGoal = distanceToGoal(simulator.state.position).toFloat(),
            density = 1.0f,
        )

    private fun localGoal(): Pair<Vec3d, Vec3d> {
        if (segmentMode) return target to desiredVelocity
        val index = (routeCursor + LOCAL_GOAL_LOOKAHEAD).coerceAtMost(routeNodes.lastIndex)
        val position = routeNodes[index]
        if (index == routeNodes.lastIndex) return position to Vec3d.ZERO
        val direction = horizontalDirection(routeNodes[(index - 1).coerceAtLeast(0)], position)
        return position to Vec3d(direction.x * INTERMEDIATE_GOAL_SPEED, 0.0, direction.z * INTERMEDIATE_GOAL_SPEED)
    }

    private fun observation(): FloatArray {
        val state = simulator.state
        val position = state.position
        val result = FloatArray(OBSERVATION_SIZE)
        val (goal, goalVelocity) = localGoal()
        var i = 0

        result[i++] = ((goal.x - position.x) / GOAL_SCALE_X).toFloat()
        result[i++] = ((goal.y - position.y) / GOAL_SCALE_Y).toFloat()
        result[i++] = ((goal.z - position.z) / GOAL_SCALE_Z).toFloat()
        result[i++] = (state.velocity.x / VELOCITY_SCALE).toFloat()
        result[i++] = (state.velocity.y / VELOCITY_SCALE).toFloat()
        result[i++] = (state.velocity.z / VELOCITY_SCALE).toFloat()
        val yawRadians = state.rotation.yaw * PI / 180.0
        result[i++] = sin(yawRadians).toFloat()
        result[i++] = cos(yawRadians).toFloat()
        result[i++] = if (state.onGround) 1.0f else 0.0f
        result[i++] = if (state.isSprinting) 1.0f else 0.0f
        result[i++] = state.jumpingCooldown / 10.0f
        result[i++] = if (state.horizontalCollision) 1.0f else 0.0f
        result[i++] = if (state.verticalCollision) 1.0f else 0.0f
        result[i++] = (ticks.toDouble() / maxTicks).toFloat()
        result[i++] = ((routeNodes.lastIndex - routeCursor).toDouble() / routeNodes.size).toFloat()
        result[i++] = (goalVelocity.x / DESIRED_VELOCITY_SCALE).toFloat()
        result[i++] = (goalVelocity.z / DESIRED_VELOCITY_SCALE).toFloat()
        result[i++] = 1.0f
        for (offset in ANCHOR_OFFSETS) {
            val anchor = routeNodes[(routeCursor + offset).coerceAtMost(routeNodes.lastIndex)]
            result[i++] = ((anchor.x - position.x) / ANCHOR_SCALE_XZ).toFloat()
            result[i++] = ((anchor.y - position.y) / ANCHOR_SCALE_Y).toFloat()
            result[i++] = ((anchor.z - position.z) / ANCHOR_SCALE_XZ).toFloat()
        }
        result[i++] = (previousValue / VALUE_NORM).toFloat()
        result[i++] = (valuePlan.tailCosts[coarseCursor()].lowerBound / VALUE_NORM).toFloat()
        result[i++] = ((startValue - previousValue) / startValue).toFloat()
        check(i == DENSE_OBSERVATIONS)

        val centerX = floor(position.x).toInt()
        val centerY = floor(position.y).toInt()
        val centerZ = floor(position.z).toInt()
        for (dy in VOXEL_MIN_DY..VOXEL_MAX_DY) {
            for (dx in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                for (dz in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                    result[i++] = if (
                        BlockPos(centerX + dx, centerY + dy, centerZ + dz) in SOLID
                    ) 1.0f else 0.0f
                }
            }
        }
        check(i == result.size)
        return result
    }

    private fun advanceRouteCursor(position: Vec3d) {
        val searchEnd = (routeCursor + 8).coerceAtMost(routeNodes.lastIndex)
        var bestIndex = routeCursor
        var bestDistance = nodeDistanceSquared(position, routeNodes[routeCursor])
        for (index in routeCursor + 1..searchEnd) {
            val distance = nodeDistanceSquared(position, routeNodes[index])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        routeCursor = bestIndex
    }

    private fun progressPotential(position: Vec3d): Double {
        val point = routeNodes[routeCursor]
        val next = routeNodes[(routeCursor + 1).coerceAtMost(routeNodes.lastIndex)]
        val segmentX = next.x - point.x
        val segmentY = next.y - point.y
        val segmentZ = next.z - point.z
        val lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ
        val fraction = if (lengthSquared <= 1.0e-9) 0.0 else (
            (position.x - point.x) * segmentX +
                (position.y - point.y) * segmentY +
                (position.z - point.z) * segmentZ
            ) / lengthSquared
        return routeCursor + fraction.coerceIn(0.0, 1.0)
    }

    private fun terminalPotential(position: Vec3d, velocity: Vec3d): Double =
        -distanceToGoal(position) - horizontalVelocityError(velocity) * 1.5

    private fun distanceToGoal(position: Vec3d): Double {
        val dx = target.x - position.x
        val dy = target.y - position.y
        val dz = target.z - position.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun reachedGoal(after: MovementSimulationState, distance: Double): Boolean {
        val velocityOk = horizontalVelocityError(after.velocity) <= terminalVelocityTolerance()
        if (after.onGround && distance <= GOAL_RADIUS && velocityOk) return true
        if (!segmentMode) return false
        if (distance <= GOAL_RADIUS && velocityOk) arrivalLatch = GROUNDING_GRACE
        else if (arrivalLatch > 0) arrivalLatch--
        return arrivalLatch > 0 && after.onGround && distance <= GOAL_RADIUS * GRACE_RADIUS_MULT && velocityOk
    }

    /** V(state): admissible remaining travel time to this episode's goal, in ticks. */
    private fun valueAt(state: MovementSimulationState): Double =
        tailLowerBound(state, valuePlan, coarseCursor()).ticks

    private fun coarseCursor(): Int {
        var node = 0
        for (index in valueCoarseIndices.indices) {
            if (valueCoarseIndices[index] <= routeCursor) node = index else break
        }
        return node.coerceIn(0, valuePlan.nodes.lastIndex)
    }

    private fun horizontalVelocityError(velocity: Vec3d): Double =
        hypot(velocity.x - desiredVelocity.x, velocity.z - desiredVelocity.z)

    private fun terminalVelocityTolerance(): Double =
        if (desiredVelocity.x * desiredVelocity.x + desiredVelocity.z * desiredVelocity.z < 0.0025) {
            STOP_VELOCITY_TOLERANCE
        } else {
            MOVING_VELOCITY_TOLERANCE
        }

    private fun nodeDistanceSquared(position: Vec3d, point: Vec3d): Double {
        val dx = position.x - point.x
        val dy = position.y - point.y
        val dz = position.z - point.z
        return dx * dx + dy * dy * 2.0 + dz * dz
    }

    private fun horizontalDirection(from: Vec3d, to: Vec3d): Vec3d = horizontalDirectionOf(from, to)

    private fun minecraftYaw(direction: Vec3d): Double = minecraftYawOf(direction)

    /** Everything both the policy and the search bench need for one shared probe. */
    internal data class SegmentSetup(
        val caseIndex: Int,
        val startStance: Stance,
        val goalStance: Stance,
        val startState: MovementSimulationState,
        /** Densified sub-route feeding the policy's anchors and progress. */
        val anchorPoints: List<Vec3d>,
        val target: Vec3d,
        val desiredVelocity: Vec3d,
        val jumpCount: Int,
        /** Chain-goal-relative plan: this segment's value function. */
        val valuePlan: CoarseRoutePlan,
        /** Densified index of each [valuePlan] node within [anchorPoints]. */
        val valueCoarseIndices: IntArray,
    )

    private data class Case(
        val points: List<Vec3d>,
        val coarseIndices: IntArray,
        val plan: CoarseRoutePlan,
    )

    companion object {
        val ACTION_DIMS = BlockFieldEnvironment.ACTION_DIMS
        // Value layout: the block-field 27 dense features + 3 tail-cost features, then
        // the unchanged voxel tensor. Byte-identical to DStarTerrainEnvironment so one
        // checkpoint trains there and is measured here.
        const val DENSE_OBSERVATIONS = 30
        const val VOXEL_HALF_XZ = BlockFieldEnvironment.VOXEL_HALF_XZ
        const val VOXEL_MIN_DY = BlockFieldEnvironment.VOXEL_MIN_DY
        const val VOXEL_MAX_DY = BlockFieldEnvironment.VOXEL_MAX_DY
        const val OBSERVATION_SIZE =
            DENSE_OBSERVATIONS + (VOXEL_MAX_DY - VOXEL_MIN_DY + 1) * (VOXEL_HALF_XZ * 2 + 1) * (VOXEL_HALF_XZ * 2 + 1)
        private const val VALUE_NORM = 200.0

        const val SCENARIOS = 12
        val JUMP_ORDINAL = CoarseMoveKind.JUMP_CANDIDATE.ordinal

        /** Coarse edges chained per segment-mode episode / quality probe. */
        const val CHAIN_EDGES = 3

        private const val GOAL_SCALE_X = 48.0
        private const val GOAL_SCALE_Y = 8.0
        private const val GOAL_SCALE_Z = 25.0
        private const val VELOCITY_SCALE = 0.6
        private const val DESIRED_VELOCITY_SCALE = 0.4
        private const val ANCHOR_SCALE_XZ = 12.0
        private const val ANCHOR_SCALE_Y = 6.0
        private val ANCHOR_OFFSETS = intArrayOf(3, 7, 11)
        private val AXIS_ACTIONS = doubleArrayOf(-1.0, 0.0, 1.0)
        private val YAW_DELTAS = doubleArrayOf(-15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0)

        private const val LOCAL_GOAL_LOOKAHEAD = 34
        private const val INTERMEDIATE_GOAL_SPEED = 0.24
        private const val TICKS_PER_ROUTE_NODE = 6
        private const val DEFAULT_MAX_TICKS = 320
        private const val CHAIN_MAX_TICKS = 160

        private const val SNAP_WINDOW = 3
        private const val GROUNDING_GRACE = 10
        private const val GRACE_RADIUS_MULT = 1.6

        private const val REST_START_FRACTION = 0.5
        private const val CHAIN_MIN_ENTRY = 0.12
        private const val MAX_ENTRY_SPEED = 0.28
        private const val SPRINT_ENTRY_SPEED = 0.16
        private const val STALL_TICKS = 60
        private const val STALL_EXEMPT_RADIUS = 3.0
        private const val PROGRESS_EPSILON = 1.0e-3

        private const val GOAL_RADIUS = 0.9
        private const val STOP_VELOCITY_TOLERANCE = 0.115
        private const val MOVING_VELOCITY_TOLERANCE = 0.20
        private const val FALL_Y = 55.0
        private const val COURSE_MIN_X = -2.0
        private const val COURSE_MAX_X = BedrockFieldLayout.LENGTH + 2.0
        private const val COURSE_MIN_Z = -(BedrockFieldLayout.HALF_WIDTH + 2.0)
        private const val COURSE_MAX_Z = BedrockFieldLayout.HALF_WIDTH + 2.0
        private const val COURSE_MAX_Y = 80.0

        private const val PROGRESS_REWARD = 0.10
        private const val TERMINAL_SHAPING_REWARD = 0.08
        private const val STEP_REWARD = -0.003f
        private const val COLLISION_REWARD = -0.025f
        private const val SUCCESS_REWARD = 10.0f
        private const val FAILURE_REWARD = -10.0f

        val PROFILE = PlayerPhysicsProfile(
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

        private val SOLID: Set<BlockPos> by lazy {
            BedrockFieldLayout.solidCells().mapTo(HashSet()) { BlockPos(it.x, it.y, it.z) }
        }

        internal val ENVIRONMENT: SnapshotSimulationEnvironment by lazy {
            SnapshotSimulationEnvironment.synthetic(
                bounds = SimulationSnapshotBounds(
                    -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                    BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
                ),
                blocks = SOLID.associateWith { SnapshotBlockPhysics.FULL_CUBE },
            )
        }

        private fun horizontalDirectionOf(from: Vec3d, to: Vec3d): Vec3d {
            val dx = to.x - from.x
            val dz = to.z - from.z
            val length = hypot(dx, dz)
            return if (length <= 1.0e-9) Vec3d(1.0, 0.0, 0.0) else Vec3d(dx / length, 0.0, dz / length)
        }

        private fun minecraftYawOf(direction: Vec3d): Double =
            atan2(direction.z, direction.x) * 180.0 / PI - 90.0

        private fun isStandable(x: Int, feetY: Int, z: Int): Boolean =
            BlockPos(x, feetY - 1, z) in SOLID &&
                BlockPos(x, feetY, z) !in SOLID &&
                BlockPos(x, feetY + 1, z) !in SOLID

        private fun snapFeetY(x: Int, z: Int, nearY: Int): Int? {
            for (radius in 0..SNAP_WINDOW) {
                if (isStandable(x, nearY - radius, z)) return nearY - radius
                if (radius > 0 && isStandable(x, nearY + radius, z)) return nearY + radius
            }
            return null
        }

        private fun densify(plan: CoarseRoutePlan): Case {
            val corners = plan.nodes.map { Vec3d(it.x + 0.5, it.y.toDouble(), it.z + 0.5) }
            val points = ArrayList<Vec3d>(corners.size * 2)
            val coarseIndices = IntArray(corners.size)
            points += corners.first()
            coarseIndices[0] = 0
            for (index in 1 until corners.size) {
                val from = corners[index - 1]
                val to = corners[index]
                val steps = ceil(
                    hypot(to.x - from.x, to.z - from.z).coerceAtLeast(abs(to.y - from.y)),
                ).toInt().coerceAtLeast(1)
                for (step in 1..steps) {
                    val t = step.toDouble() / steps
                    points += Vec3d(
                        from.x + (to.x - from.x) * t,
                        from.y + (to.y - from.y) * t,
                        from.z + (to.z - from.z) * t,
                    )
                }
                coarseIndices[index] = points.lastIndex
            }
            val snapped = points.map { point ->
                val feetY = snapFeetY(floor(point.x).toInt(), floor(point.z).toInt(), Math.round(point.y).toInt())
                if (feetY != null) Vec3d(point.x, feetY.toDouble(), point.z) else point
            }
            return Case(snapped, coarseIndices, plan)
        }

        private val CASES: List<Case> by lazy {
            val moves = SimpleMoveLibrary.build(
                costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
                options = SimpleMoveOptions(),
            )
            val plans = ArrayList<CoarseRoutePlan>()
            BedrockFieldLayout.randomEndpointPairs(count = SCENARIOS).forEachIndexed { index, pair ->
                val start = Stance(pair.first.x, pair.first.y, pair.first.z)
                val goal = Stance(pair.second.x, pair.second.y, pair.second.z)
                val planner = CoarsePlanner(ENVIRONMENT, moves, start, goal)
                if (planner.repair(Duration.INFINITE).converged) {
                    planner.routePlan(snapshotRevision = index.toLong())?.let(plans::add)
                }
            }
            val cases = plans.map { densify(it) }.filter { it.plan.edges.size > CHAIN_EDGES }
            val jumpEdges = cases.sumOf { case -> case.plan.edges.count { it.kind.ordinal == JUMP_ORDINAL } }
            val totalEdges = cases.sumOf { it.plan.edges.size }
            println(
                "[bedrock-field] ${cases.size}/$SCENARIOS routes usable; " +
                    "edges=$totalEdges ($jumpEdges jumps)",
            )
            check(cases.isNotEmpty()) { "no usable bedrock corpus routes" }
            cases
        }

        /** The number of usable corpus routes, for the bench to iterate cases directly. */
        internal val caseCount: Int get() = CASES.size

        /**
         * The shared probe: seed -> a chain of [CHAIN_EDGES] coarse edges with a moving
         * entry and a stop goal. Deterministic in [seed] alone so the policy (over the
         * bridge) and the search bench realise byte-identical start states and endpoints.
         */
        internal fun segmentSetup(seed: Long): SegmentSetup {
            val random = SplittableRandom(seed)
            val caseIndex = random.nextInt(CASES.size)
            val case = CASES[caseIndex]
            val edgeCount = case.plan.edges.size
            val firstEdge = random.nextInt(edgeCount - CHAIN_EDGES + 1)
            val lastEdge = firstEdge + CHAIN_EDGES - 1
            val startNode = firstEdge
            val goalNode = lastEdge + 1
            val startIndex = case.coarseIndices[startNode]
            val goalIndex = case.coarseIndices[goalNode]
            val anchorPoints = case.points.subList(startIndex, goalIndex + 1)
            val jumpCount = (firstEdge..lastEdge).count { case.plan.edges[it].kind.ordinal == JUMP_ORDINAL }
            val valuePlan = subPlan(case.plan, firstEdge, lastEdge)
            val valueCoarseIndices = IntArray(goalNode - startNode + 1) { case.coarseIndices[startNode + it] - startIndex }

            val startPosition = anchorPoints.first()
            val direction = horizontalDirectionOf(startPosition, anchorPoints[1.coerceAtMost(anchorPoints.lastIndex)])
            val startYaw = minecraftYawOf(direction) + random.nextDouble(-15.0, 15.0)
            val speed = random.nextDouble(CHAIN_MIN_ENTRY, MAX_ENTRY_SPEED)
            val startState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = startPosition,
                rotation = Rotation(startYaw, 0.0),
                velocity = Vec3d(direction.x * speed, 0.0, direction.z * speed),
                isSprinting = speed >= SPRINT_ENTRY_SPEED,
            )
            return SegmentSetup(
                caseIndex = caseIndex,
                startStance = case.plan.nodes[startNode],
                goalStance = case.plan.nodes[goalNode],
                startState = startState,
                anchorPoints = anchorPoints,
                target = anchorPoints.last(),
                desiredVelocity = Vec3d.ZERO,
                jumpCount = jumpCount,
                valuePlan = valuePlan,
                valueCoarseIndices = valueCoarseIndices,
            )
        }

        /**
         * A coarse edge chain re-based as its own route: tail costs become suffix sums
         * of edge lower bounds so the chain goal reads zero cost-to-go. This is the
         * value function the policy sees for a segment, matching what it was trained on
         * (cost-to-go to the local goal, not the distant original goal).
         */
        private fun subPlan(plan: CoarseRoutePlan, firstEdge: Int, lastEdge: Int): CoarseRoutePlan {
            val nodes = plan.nodes.subList(firstEdge, lastEdge + 2)
            val edges = plan.edges.subList(firstEdge, lastEdge + 1)
            val tail = DoubleArray(nodes.size)
            for (index in edges.indices.reversed()) tail[index] = tail[index + 1] + edges[index].lowerBoundTicks
            return plan.copy(
                nodes = nodes.toList(),
                edges = edges.toList(),
                tailCosts = nodes.indices.map { TailCost.Exact(tail[it], plan.routeVersion) },
                lowerBoundTicks = tail[0],
            )
        }
    }
}
