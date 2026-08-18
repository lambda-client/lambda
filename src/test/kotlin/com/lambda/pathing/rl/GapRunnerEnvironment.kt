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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Small goal-conditioned Minecraft movement environment used to test whether a
 * learned policy can become the informed low-level oracle for the path planner.
 *
 * The course is deliberately simple: a finite flat runway, sometimes interrupted
 * by a one-to-three-block full-width gap. Start yaw, lateral position, target and
 * gap location vary every episode. All motion is advanced by the production
 * [MovementSimulator], so training cannot silently learn different physics.
 *
 * This lives in the test source set because it is an experiment and must not ship
 * inside the mod jar. [RlBridgeServer] exposes it to Python/Sample Factory.
 */
class GapRunnerEnvironment : RlEnvironment {
    private lateinit var simulator: MovementSimulator
    private lateinit var target: Vec3d
    private var gapStart = NO_GAP
    private var gapWidth = 0
    private var ticks = 0
    private var previousDistance = 0.0

    override fun reset(seed: Long, maxDifficulty: Int): RlTransition {
        val random = SplittableRandom(seed)
        gapWidth = sampleGapWidth(random)
        gapStart = if (gapWidth == 0) NO_GAP else random.nextInt(6, 11)

        val startZ = random.nextDouble(-1.5, 1.5)
        val targetX = if (gapWidth == 0) {
            random.nextDouble(14.0, 22.0)
        } else {
            random.nextDouble(gapStart + gapWidth + 5.0, gapStart + gapWidth + 10.0)
        }
        target = Vec3d(targetX, FEET_Y, random.nextDouble(-1.75, 1.75))

        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = BOUNDS,
            blocks = buildMap {
                for (x in FLOOR_MIN_X..FLOOR_MAX_X) {
                    if (x in gapRange()) continue
                    for (z in -HALF_WIDTH..HALF_WIDTH) {
                        put(BlockPos(x, FLOOR_Y, z), SnapshotBlockPhysics.FULL_CUBE)
                    }
                }
            },
        )
        val start = Vec3d(1.5, FEET_Y, startZ)
        val initialState = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = start,
            rotation = Rotation(random.nextDouble(-112.0, -68.0), 0.0),
        )
        simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = initialState,
            skipEntityCollisions = true,
        )
        ticks = 0
        previousDistance = horizontalDistance(start)
        return transition(reward = 0.0f, outcome = RlOutcome.RUNNING)
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

        val beforeDistance = previousDistance
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
            return transition(
                reward = FAILURE_REWARD,
                outcome = RlOutcome.SIMULATION_REJECTED,
            )
        }

        val after = simulator.state
        val distance = horizontalDistance(after.position)
        previousDistance = distance
        var reward = ((beforeDistance - distance) * PROGRESS_REWARD).toFloat() + STEP_REWARD
        if (after.horizontalCollision) reward += COLLISION_REWARD
        if (input.jump) reward += JUMP_PRESS_REWARD

        val outcome = when {
            after.position.y < FALL_Y -> RlOutcome.FALL
            after.position.x !in COURSE_MIN_X..COURSE_MAX_X ||
                abs(after.position.z) > COURSE_MAX_ABS_Z -> RlOutcome.OUT_OF_BOUNDS
            after.onGround && distance <= GOAL_RADIUS -> RlOutcome.SUCCESS
            ticks >= MAX_TICKS -> RlOutcome.TIME_LIMIT
            else -> RlOutcome.RUNNING
        }
        reward += when (outcome) {
            RlOutcome.SUCCESS -> SUCCESS_REWARD
            RlOutcome.FALL,
            RlOutcome.OUT_OF_BOUNDS,
            RlOutcome.SIMULATION_REJECTED,
            // The flat runway has no stall detector; listed so that adding one
            // later is a compile error here rather than a silent zero reward.
            RlOutcome.STALLED -> FAILURE_REWARD
            RlOutcome.RUNNING, RlOutcome.TIME_LIMIT -> 0.0f
        }
        return transition(reward, outcome)
    }

    private fun transition(reward: Float, outcome: RlOutcome): RlTransition {
        val distance = horizontalDistance(simulator.state.position).toFloat()
        return RlTransition(
            observation = observation(),
            reward = reward,
            terminated = outcome != RlOutcome.RUNNING && outcome != RlOutcome.TIME_LIMIT,
            truncated = outcome == RlOutcome.TIME_LIMIT,
            outcome = outcome,
            ticks = ticks,
            scenario = gapWidth,
            distanceToGoal = distance,
            density = 1.0f,
        )
    }

    /**
     * Dense state followed by a target-axis-aligned floor occupancy window.
     *
     * The first experiment only has one floor height, so a 2-D support map is
     * sufficient. A later bedrock experiment will replace this with a local 3-D
     * collision tensor and high-level value-field channels.
     */
    private fun observation(): FloatArray {
        val state = simulator.state
        val position = state.position
        val result = FloatArray(OBSERVATION_SIZE)
        var i = 0
        result[i++] = ((target.x - position.x) / 24.0).toFloat()
        result[i++] = ((target.z - position.z) / 8.0).toFloat()
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
        result[i++] = (horizontalDistance(position) / 24.0).toFloat()
        result[i++] = (ticks.toDouble() / MAX_TICKS).toFloat()
        result[i++] = (position.z / HALF_WIDTH).toFloat()

        val centerX = floor(position.x).toInt()
        val centerZ = floor(position.z).toInt()
        for (dx in OBS_MIN_DX..OBS_MAX_DX) {
            for (dz in -OBS_HALF_WIDTH..OBS_HALF_WIDTH) {
                result[i++] = if (hasFloor(centerX + dx, centerZ + dz)) 1.0f else 0.0f
            }
        }
        check(i == result.size)
        return result
    }

    private fun hasFloor(x: Int, z: Int): Boolean =
        x in FLOOR_MIN_X..FLOOR_MAX_X &&
            z in -HALF_WIDTH..HALF_WIDTH &&
            x !in gapRange()

    private fun gapRange(): IntRange =
        if (gapWidth == 0) IntRange.EMPTY else gapStart until gapStart + gapWidth

    private fun horizontalDistance(position: Vec3d): Double =
        hypot(target.x - position.x, target.z - position.z)

    private fun sampleGapWidth(random: SplittableRandom): Int {
        val sample = random.nextInt(100)
        return when {
            sample < 40 -> 0
            sample < 62 -> 1
            sample < 84 -> 2
            else -> 3
        }
    }

    companion object {
        val ACTION_DIMS = intArrayOf(3, 3, 2, 2, 7)

        const val DENSE_OBSERVATIONS = 15
        const val OBS_MIN_DX = -2
        const val OBS_MAX_DX = 10
        const val OBS_HALF_WIDTH = 3
        const val OBSERVATION_SIZE =
            DENSE_OBSERVATIONS + (OBS_MAX_DX - OBS_MIN_DX + 1) * (OBS_HALF_WIDTH * 2 + 1)

        const val MAX_TICKS = 160

        private const val FLOOR_Y = 0
        private const val FEET_Y = 1.0
        private const val FLOOR_MIN_X = -4
        private const val FLOOR_MAX_X = 32
        private const val HALF_WIDTH = 4
        private const val NO_GAP = Int.MIN_VALUE
        private const val FALL_Y = -2.0
        private const val COURSE_MIN_X = -3.0
        private const val COURSE_MAX_X = 33.0
        private const val COURSE_MAX_ABS_Z = 4.45
        private const val GOAL_RADIUS = 0.75

        private const val PROGRESS_REWARD = 0.6
        private const val STEP_REWARD = -0.002f
        private const val COLLISION_REWARD = -0.03f
        private const val JUMP_PRESS_REWARD = -0.001f
        private const val SUCCESS_REWARD = 6.0f
        private const val FAILURE_REWARD = -6.0f

        private val AXIS_ACTIONS = doubleArrayOf(-1.0, 0.0, 1.0)
        private val YAW_DELTAS = doubleArrayOf(-15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0)
        private val BOUNDS = SimulationSnapshotBounds(-8, -8, -8, 36, 10, 8)
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
