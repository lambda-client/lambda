/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The corridor follower: pure pursuit along the coarse polyline, a physics-latched
 * brake, a terminal approach, and replayed launch frames.
 *
 * Extracted from `WalkingSeedSearch` so one controller object serves both the legacy
 * whole-route sweep and the anchor search (invariant 3: search and replay must drive
 * the same controller, never two implementations that agree by luck).
 */
internal class CorridorFollowerProgram(
    stanceNodes: List<Stance>,
    private val parameters: WalkingSeedParameters,
    private val config: WalkingSeedSearchConfig,
) : ControlProgram {
    private val maxYawChange = config.maxYawDegreesPerFrame
    private val nodes = stanceNodes.map { it.center() }

    /**
     * Rises this program will jump on its step-up schedule.
     *
     * Empty when no lead distance was chosen: the route's rises are then a gap
     * launch's business, not the step-up schedule's. Deriving these from node
     * geometry alone left `nextRise` stuck at zero -- `shouldJump` bails before
     * advancing it -- so a rise stayed permanently "pending" and the brake, which
     * waits for pending rises, never latched. The body then walked at full
     * throttle through the goal forever.
     */
    private val rises = if (parameters.stepUpJumpLeadDistance == null) {
        emptyList()
    } else {
        stanceNodes.zipWithNext().mapIndexedNotNull { index, (from, to) ->
            index.takeIf { to.y > from.y }
        }
    }

    /** Polyline distance from each node to the goal; the tail of the route. */
    private val distanceToGoal = DoubleArray(nodes.size).also { suffix ->
        for (index in nodes.lastIndex - 1 downTo 0) {
            suffix[index] = suffix[index + 1] + horizontalDistance(nodes[index], nodes[index + 1])
        }
    }

    /**
     * Pure-pursuit progress. It only ever advances, and by at most
     * [MAX_PROGRESS_ADVANCE] nodes per tick: a route that doubles back passes
     * close to its own earlier nodes, and a global nearest-node search would
     * snap the target across the fold and steer straight through the obstacle.
     */
    private var progressIndex = 0
    private var braking = false
    private var terminalApproach = false
    private var terminalReleased = false
    private var nextRise = 0
    private var jumpWasAirborne = false

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        advanceProgress(observed)

        // Resolves this tick's launch and retires a rise once its landing is
        // observed, so it must run before the brake decision consumes it.
        // A gap launch is a *discovered* frame, not a geometric lead: backtracking
        // over a failed walk is what found it, so it is replayed by index.
        val gapLaunch = frame in parameters.gapLaunchFrames && observed.onGround
        val jump = shouldJump(observed) || gapLaunch

        // Brake on distance remaining *along the route*, not straight-line
        // distance to the goal: around an obstacle the player can be a stride
        // from the goal as the crow flies while most of the path is still
        // ahead, and braking there stalls short of the corner. A pending rise
        // also holds the brake off -- a coasting player has no momentum to
        // clear a step-up, so a rise on the final edge could never launch.
        val risePending = nextRise < rises.size
        val gapPending = parameters.gapLaunchFrames.any { frame <= it }

        // Once the body has stopped short of a tight goal, closing the last fraction
        // of a block is its own mode -- not more braking. The old code set the flag
        // and then fell straight back into the brake test below (remaining distance
        // is *inside* brakeDistance, that is what "short" means), so it re-braked to a
        // standstill every tick and never actually moved. The body then sat one
        // stride from the goal until the frame budget expired: "no stable stop after
        // 160 frames" with the closest attempt 0.23 blocks out.
        if (terminalApproach) return terminalApproachInput(observed, jump)

        if (braking) {
            val stoppedShort = observed.onGround &&
                observed.velocity.horizontalLength() <= config.stoppedSpeed &&
                hypot(
                    nodes.last().x - observed.position.x,
                    nodes.last().z - observed.position.z,
                ) > config.goalRadius
            if (stoppedShort) {
                braking = false
                terminalApproach = true
                return terminalApproachInput(observed, jump)
            }
            return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
        }
        // Brake when the committed coast will land the body just SHORT of the goal,
        // not at a fixed distance: from sprint speed a small fixed latch releases
        // too late, coasts past the goal, and the terminal approach has to walk
        // back -- the reported overshoot-and-nudge. The coast is a known closed
        // form (speed * 2.2), so braking at that distance plus a short bias stops
        // inside the goal radius on the near side. The swept brakeDistance still
        // matters when it is *larger* (an earlier, conservative stop).
        val coastStop = observed.velocity.horizontalLength() * TERMINAL_COAST_PER_SPEED +
            BRAKE_SHORT_BIAS_BLOCKS
        if (!risePending && !gapPending &&
            remainingPathDistance(observed) <= maxOf(parameters.brakeDistance, coastStop)
        ) braking = true
        if (braking) {
            return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
        }

        val target = nodes[minOf(nodes.lastIndex, progressIndex + parameters.lookAheadNodes)]
        val desiredYaw = Math.toDegrees(atan2(target.z - observed.position.z, target.x - observed.position.x)) - 90.0
        val yawDelta = Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
        return MovementSimulationInput(
            forward = 1.0,
            sprint = parameters.sprint,
            jump = jump,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

    /**
     * Closes the last fraction of a block to a goal the body stopped short of.
     *
     * A plain non-sprint walk that releases -- and *latches* released
     * ([terminalReleased]) -- the moment either the body enters the goal radius or its
     * committed momentum will coast it there. Each piece is forced on us:
     *
     * - **Not sneak.** The manager writes its input after vanilla's `input.tick()` has
     *   already built the movement vector, so a pressed sneak never slows the live body;
     *   the simulator would certify a slowdown the client cannot reproduce (a live
     *   differential caught exactly this). Only a full walk is honest.
     * - **Release inside the radius, do not steer to the centre.** A walk from rest
     *   barely reaches the near edge, but chasing the centre overshoots it -- and once
     *   past, `atan2` flips 180° while the 30 deg/tick yaw cap cannot turn the body
     *   around, so it drives far past and slowly loops back. That loop *is* the
     *   circle-around-the-goal. Stopping at the near edge is inside the radius and does
     *   not trigger it.
     * - **The coast term** ([TERMINAL_COAST_PER_SPEED]) only matters for a faster entry:
     *   release early enough that friction lands the body in the radius rather than
     *   through it.
     * - **The latch** keeps a post-release drift from re-pressing and reopening the loop.
     */
    private fun terminalApproachInput(
        observed: MovementSimulationState,
        jump: Boolean,
    ): MovementSimulationInput {
        val goal = nodes.last()
        val dx = goal.x - observed.position.x
        val dz = goal.z - observed.position.z
        val distance = hypot(dx, dz)

        // Speed already committed toward the goal, and how far that coasts once the key
        // is released -- a decaying-friction geometric series, ~2.2 blocks per b/t.
        val speedTowardGoal = if (distance <= 1e-9) 0.0
        else (observed.velocity.x * dx + observed.velocity.z * dz) / distance
        val coast = speedTowardGoal * TERMINAL_COAST_PER_SPEED

        if (terminalReleased || distance <= config.goalRadius || coast >= distance) {
            terminalReleased = true
            return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
        }
        val desiredYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
        val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)
        // A stop *past* the goal needs a ~180 degree turn. Walking while the yaw cap
        // crawls around traces a little circle through the goal -- the reported
        // "circle at the goal". Pivot in place until the heading roughly leads the
        // walk; only then move.
        val forward = if (abs(yawError) > TERMINAL_PIVOT_YAW_DEGREES) 0.0 else 1.0
        return MovementSimulationInput(
            forward = forward,
            sprint = false,
            jump = jump,
            rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
        )
    }

    private fun advanceProgress(observed: MovementSimulationState) {
        val limit = minOf(nodes.lastIndex, progressIndex + MAX_PROGRESS_ADVANCE)
        var best = progressIndex
        var bestSquared = horizontalDistanceSquared(nodes[progressIndex], observed)
        for (index in progressIndex + 1..limit) {
            val squared = horizontalDistanceSquared(nodes[index], observed)
            if (squared < bestSquared) {
                bestSquared = squared
                best = index
            }
        }
        progressIndex = best
    }

    private fun remainingPathDistance(observed: MovementSimulationState): Double {
        val next = minOf(nodes.lastIndex, progressIndex + 1)
        val toNext = hypot(nodes[next].x - observed.position.x, nodes[next].z - observed.position.z)
        return toNext + distanceToGoal[next]
    }

    private fun shouldJump(observed: MovementSimulationState): Boolean {
        val leadDistance = parameters.stepUpJumpLeadDistance ?: return false
        if (nextRise >= rises.size) return false
        if (!observed.onGround) {
            jumpWasAirborne = true
            return false
        }
        if (jumpWasAirborne) {
            nextRise++
            jumpWasAirborne = false
            if (nextRise >= rises.size) return false
        }
        val takeoff = nodes[rises[nextRise]]
        return hypot(takeoff.x - observed.position.x, takeoff.z - observed.position.z) <= leadDistance
    }

    private fun horizontalDistanceSquared(node: HorizontalPoint, observed: MovementSimulationState): Double {
        val dx = node.x - observed.position.x
        val dz = node.z - observed.position.z
        return dx * dx + dz * dz
    }

    private companion object {
        /**
         * Heading error above which the terminal approach turns in place instead of
         * walking. At the 30 deg/tick cap a 45 degree error closes in two ticks of
         * pivot; walking through it arcs away from a goal this close.
         */
        const val TERMINAL_PIVOT_YAW_DEGREES = 45.0

        /**
         * How far short of the goal centre the physics-latched brake aims. Inside
         * the 0.20 goal radius, so the coasted stop is already an arrival and the
         * terminal nudge never runs; landing long would put the body past the goal.
         */
        const val BRAKE_SHORT_BIAS_BLOCKS = 0.08

        /**
         * Blocks a released walk coasts per b/t of committed speed. On ground the
         * next tick still advances by the full velocity before friction (~0.546)
         * decays it, so the coast sums to speed / (1 - 0.546) ~= 2.2. The terminal
         * approach releases when this coast reaches the goal, so a faster entry drifts
         * to rest in the radius rather than through it.
         */
        const val TERMINAL_COAST_PER_SPEED = 2.2
    }
}
