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
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
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
import kotlin.time.Duration

/**
 * Value-guided movement training over D*-planned irregular terrain.
 *
 * Every reset builds a fresh randomised bedrock-like patch, plans a real coarse route
 * with [CoarsePlanner], and rewards the policy for reducing the route's exact admissible
 * cost-to-go — the AlphaZero triad with the lazy graph as the value function:
 *
 *   reward = (V(before) - V(after) - 1) x [TIME_REWARD_SCALE]
 *
 * where V is [tailLowerBound] in ticks. Potential-based shaping (Ng et al.): the optimal
 * policy is unchanged, the true objective is total time to the goal, and a tick that
 * reduces the admissible bound by more than one is the policy finding a line that beats
 * the coarse grid's own time estimate. **There is no collision term** — a collision is
 * only bad when it is slow, which the time objective already prices; soft wall-slides
 * that keep speed are a legitimately faster line.
 *
 * The observation is byte-identical to [BedrockFieldEnvironment]'s value layout so one
 * checkpoint trains here and is measured on the bedrock quality bench.
 */
class DStarTerrainEnvironment : RlEnvironment {
    private lateinit var simulator: MovementSimulator
    private lateinit var environment: SnapshotSimulationEnvironment
    private lateinit var solid: Set<BlockPos>
    private lateinit var plan: CoarseRoutePlan
    private lateinit var anchors: List<Vec3d>
    private lateinit var coarseIndices: IntArray
    private var goalStance = Stance(0, 0, 0)
    private var target = Vec3d.ZERO
    private var startValue = 1.0
    private var maxTicks = 240
    private var ticks = 0
    private var routeCursor = 0
    private var previousValue = 0.0
    private var bestValue = 0.0
    private var ticksSinceProgress = 0

    override fun reset(seed: Long, maxDifficulty: Int): RlTransition {
        val random = SplittableRandom(seed)
        var built = false
        var attempt = 0
        while (!built && attempt < GENERATION_ATTEMPTS) {
            built = tryBuild(seed + attempt * 0x9E3779B97F4A7C15uL.toLong(), random)
            attempt++
        }
        check(built) { "could not generate a plannable terrain patch for seed $seed" }

        val startState = spawnState(random)
        simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = startState,
            skipEntityCollisions = true,
        )
        startValue = valueAt(startState).coerceAtLeast(1.0)
        maxTicks = (startValue * MAX_TICK_MULTIPLIER).toInt().coerceIn(MIN_TICKS, MAX_TICKS)
        ticks = 0
        routeCursor = 0
        previousValue = startValue
        bestValue = startValue
        ticksSinceProgress = 0
        return transition(0.0f, RlOutcome.RUNNING)
    }

    private fun tryBuild(seed: Long, random: SplittableRandom): Boolean {
        val cells = BedrockFieldLayout.solidCells(length = PATCH, halfWidth = HALF, seed = seed.toInt())
        val surface = BedrockFieldLayout.standableSurface(cells, PATCH, HALF)
        if (surface.size < 8) return false
        val pairs = BedrockFieldLayout.randomEndpointPairs(
            count = 1,
            length = PATCH,
            halfWidth = HALF,
            terrainSeed = seed.toInt(),
            pairSeed = (seed xor 0x51A7).toInt(),
            minHorizontalDistance = PATCH / 3.0,
        )
        val (from, to) = pairs.first()
        solid = cells.mapTo(HashSet()) { BlockPos(it.x, it.y, it.z) }
        environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-2, 56, -HALF - 2, PATCH + 1, 71, HALF + 2),
            blocks = solid.associateWith { SnapshotBlockPhysics.FULL_CUBE },
        )
        val planner = CoarsePlanner(
            environment, MOVES,
            Stance(from.x, from.y, from.z),
            Stance(to.x, to.y, to.z),
        )
        if (!planner.repair(Duration.INFINITE).converged) return false
        val built = planner.routePlan(snapshotRevision = seed) ?: return false
        if (built.nodes.size < MIN_ROUTE_NODES) return false
        plan = built
        goalStance = built.nodes.last()
        val densified = densify(built)
        anchors = densified.first
        coarseIndices = densified.second
        target = anchors.last()
        return true
    }

    private fun spawnState(random: SplittableRandom): MovementSimulationState {
        val start = anchors.first()
        val direction = horizontalDirection(start, anchors[1.coerceAtMost(anchors.lastIndex)])
        val startYaw = minecraftYaw(direction) + random.nextDouble(-20.0, 20.0)
        val speed = if (random.nextDouble() < REST_START_FRACTION) 0.0 else random.nextDouble(0.0, MAX_ENTRY_SPEED)
        return MovementSimulationState.synthetic(
            profile = PROFILE,
            position = start,
            rotation = Rotation(startYaw, 0.0),
            velocity = Vec3d(direction.x * speed, 0.0, direction.z * speed),
            isSprinting = speed >= SPRINT_ENTRY_SPEED,
        )
    }

    override fun step(action: IntArray): RlTransition {
        require(action.size == ACTION_DIMS.size) {
            "Expected ${ACTION_DIMS.size} action components, got ${action.size}"
        }
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
        val value = valueAt(after)
        // The whole reward: reduce the exact admissible time-to-go by more than the one
        // tick it cost. No progress potential, no collision toll -- time is the objective.
        var reward = ((previousValue - value) - 1.0).toFloat() * TIME_REWARD_SCALE
        previousValue = value
        if (value < bestValue - VALUE_EPSILON) {
            bestValue = value
            ticksSinceProgress = 0
        } else {
            ticksSinceProgress++
        }

        val distance = distanceToGoal(after.position)
        val outcome = when {
            after.position.y < FALL_Y -> RlOutcome.FALL
            outOfBounds(after.position) -> RlOutcome.OUT_OF_BOUNDS
            after.onGround && distance <= GOAL_RADIUS && horizontalSpeed(after.velocity) <= STOP_TOLERANCE ->
                RlOutcome.SUCCESS
            ticksSinceProgress >= STALL_TICKS && distance > STALL_EXEMPT_RADIUS -> RlOutcome.STALLED
            ticks >= maxTicks -> RlOutcome.TIME_LIMIT
            else -> RlOutcome.RUNNING
        }
        reward += when (outcome) {
            RlOutcome.SUCCESS -> SUCCESS_REWARD
            RlOutcome.FALL, RlOutcome.OUT_OF_BOUNDS, RlOutcome.SIMULATION_REJECTED, RlOutcome.STALLED -> FAILURE_REWARD
            RlOutcome.RUNNING, RlOutcome.TIME_LIMIT -> 0.0f
        }
        return transition(reward, outcome)
    }

    /** V(state): the admissible remaining travel time from an exact continuous state. */
    private fun valueAt(state: MovementSimulationState): Double =
        tailLowerBound(state, plan, coarseCursor()).ticks

    /** Coarse node at or just behind the body, for the tail-bound lookahead window. */
    private fun coarseCursor(): Int {
        var node = 0
        for (index in coarseIndices.indices) {
            if (coarseIndices[index] <= routeCursor) node = index else break
        }
        return node.coerceIn(0, plan.nodes.lastIndex)
    }

    private fun transition(reward: Float, outcome: RlOutcome): RlTransition =
        RlTransition(
            observation = observation(),
            reward = reward,
            terminated = outcome != RlOutcome.RUNNING && outcome != RlOutcome.TIME_LIMIT,
            truncated = outcome == RlOutcome.TIME_LIMIT,
            outcome = outcome,
            ticks = ticks,
            scenario = 0,
            distanceToGoal = distanceToGoal(simulator.state.position).toFloat(),
            density = 1.0f,
        )

    private fun observation(): FloatArray {
        val state = simulator.state
        val position = state.position
        val result = FloatArray(OBSERVATION_SIZE)
        var i = 0

        result[i++] = ((target.x - position.x) / GOAL_SCALE_X).toFloat()
        result[i++] = ((target.y - position.y) / GOAL_SCALE_Y).toFloat()
        result[i++] = ((target.z - position.z) / GOAL_SCALE_Z).toFloat()
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
        result[i++] = ((anchors.lastIndex - routeCursor).toDouble() / anchors.size).toFloat()
        result[i++] = 0.0f // desired terminal velocity x (stop goal)
        result[i++] = 0.0f // desired terminal velocity z (stop goal)
        result[i++] = 1.0f // terrain density placeholder, kept for layout parity
        for (offset in ANCHOR_OFFSETS) {
            val anchor = anchors[(routeCursor + offset).coerceAtMost(anchors.lastIndex)]
            result[i++] = ((anchor.x - position.x) / ANCHOR_SCALE_XZ).toFloat()
            result[i++] = ((anchor.y - position.y) / ANCHOR_SCALE_Y).toFloat()
            result[i++] = ((anchor.z - position.z) / ANCHOR_SCALE_XZ).toFloat()
        }
        // Value-function features: current cost-to-go, the node cost-to-go, and the
        // fraction of the initial time-to-go already eliminated.
        result[i++] = (previousValue / VALUE_NORM).toFloat()
        result[i++] = (plan.tailCosts[coarseCursor()].lowerBound / VALUE_NORM).toFloat()
        result[i++] = ((startValue - previousValue) / startValue).toFloat()
        check(i == DENSE_OBSERVATIONS)

        val centerX = floor(position.x).toInt()
        val centerY = floor(position.y).toInt()
        val centerZ = floor(position.z).toInt()
        for (dy in VOXEL_MIN_DY..VOXEL_MAX_DY) {
            for (dx in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                for (dz in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                    result[i++] = if (BlockPos(centerX + dx, centerY + dy, centerZ + dz) in solid) 1.0f else 0.0f
                }
            }
        }
        check(i == result.size)
        return result
    }

    private fun advanceRouteCursor(position: Vec3d) {
        val searchEnd = (routeCursor + 8).coerceAtMost(anchors.lastIndex)
        var bestIndex = routeCursor
        var bestDistance = nodeDistanceSquared(position, anchors[routeCursor])
        for (index in routeCursor + 1..searchEnd) {
            val distance = nodeDistanceSquared(position, anchors[index])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        routeCursor = bestIndex
    }

    private fun distanceToGoal(position: Vec3d): Double {
        val dx = target.x - position.x
        val dy = target.y - position.y
        val dz = target.z - position.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun outOfBounds(position: Vec3d): Boolean =
        position.x !in -2.0..(PATCH + 2.0) ||
            position.z !in -(HALF + 2.0)..(HALF + 2.0) ||
            position.y > 80.0

    private fun horizontalSpeed(velocity: Vec3d): Double = hypot(velocity.x, velocity.z)

    private fun nodeDistanceSquared(position: Vec3d, point: Vec3d): Double {
        val dx = position.x - point.x
        val dy = position.y - point.y
        val dz = position.z - point.z
        return dx * dx + dy * dy * 2.0 + dz * dz
    }

    private fun horizontalDirection(from: Vec3d, to: Vec3d): Vec3d {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val length = hypot(dx, dz)
        return if (length <= 1.0e-9) Vec3d(1.0, 0.0, 0.0) else Vec3d(dx / length, 0.0, dz / length)
    }

    private fun minecraftYaw(direction: Vec3d): Double = atan2(direction.z, direction.x) * 180.0 / PI - 90.0

    companion object {
        val ACTION_DIMS = BlockFieldEnvironment.ACTION_DIMS
        const val DENSE_OBSERVATIONS = 30
        const val VOXEL_HALF_XZ = BlockFieldEnvironment.VOXEL_HALF_XZ
        const val VOXEL_MIN_DY = BlockFieldEnvironment.VOXEL_MIN_DY
        const val VOXEL_MAX_DY = BlockFieldEnvironment.VOXEL_MAX_DY
        const val OBSERVATION_SIZE =
            DENSE_OBSERVATIONS + (VOXEL_MAX_DY - VOXEL_MIN_DY + 1) * (VOXEL_HALF_XZ * 2 + 1) * (VOXEL_HALF_XZ * 2 + 1)

        private const val PATCH = 48
        private const val HALF = 8
        private const val GENERATION_ATTEMPTS = 6
        private const val MIN_ROUTE_NODES = 6

        private const val GOAL_SCALE_X = 48.0
        private const val GOAL_SCALE_Y = 8.0
        private const val GOAL_SCALE_Z = 25.0
        private const val VELOCITY_SCALE = 0.6
        private const val ANCHOR_SCALE_XZ = 12.0
        private const val ANCHOR_SCALE_Y = 6.0
        private const val VALUE_NORM = 200.0
        private val ANCHOR_OFFSETS = intArrayOf(3, 7, 11)
        private val AXIS_ACTIONS = doubleArrayOf(-1.0, 0.0, 1.0)
        private val YAW_DELTAS = doubleArrayOf(-15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0)

        private const val TIME_REWARD_SCALE = 0.1f
        private const val SUCCESS_REWARD = 10.0f
        private const val FAILURE_REWARD = -10.0f

        private const val MAX_TICK_MULTIPLIER = 2.5
        private const val MIN_TICKS = 120
        private const val MAX_TICKS = 420
        private const val REST_START_FRACTION = 0.3
        private const val MAX_ENTRY_SPEED = 0.28
        private const val SPRINT_ENTRY_SPEED = 0.16
        private const val STALL_TICKS = 60
        private const val STALL_EXEMPT_RADIUS = 3.0
        private const val VALUE_EPSILON = 1.0e-3
        private const val GOAL_RADIUS = 0.9
        private const val STOP_TOLERANCE = 0.115
        private const val FALL_Y = 55.0

        private val MOVES = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )

        val PROFILE = BedrockFieldEnvironment.PROFILE

        private fun densify(plan: CoarseRoutePlan): Pair<List<Vec3d>, IntArray> {
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
            return points to coarseIndices
        }
    }
}
