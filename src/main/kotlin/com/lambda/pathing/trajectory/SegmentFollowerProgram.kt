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
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * When a transition presses jump: on the [delayFrames]-th grounded tick after its
 * anchor.
 *
 * Counted from the anchor, not from an absolute tape frame and not from a fixed point
 * on the edge. An absolute frame only means something inside the one whole-route rollout
 * that discovered it. A fixed point on the edge looks principled and is not: an anchor
 * created by the previous landing can already lie past every offset in the lattice, and
 * then all of them fire on the same tick and the "lattice" is one candidate. A delay is
 * always relative to where the body actually is, so the family is always diverse — and
 * the arc probe's measured launch point still chooses which delay is tried first.
 */
internal class LaunchTrigger(private val delayFrames: Int) {
    private var groundedTicks = 0
    private var fired = false

    val hasFired: Boolean get() = fired

    fun press(observed: MovementSimulationState): Boolean {
        if (fired || !observed.onGround) return false
        if (groundedTicks++ < delayFrames) return false
        fired = true
        return true
    }
}

/** Heading error above which an easing transition coasts instead of driving. */
private const val EASE_TURN_DEGREES = 50.0

/** Signed distance from [from] toward [to], in blocks, of a point on that edge's line. */
internal fun alongEdge(
    from: HorizontalPoint,
    to: HorizontalPoint,
    x: Double,
    z: Double,
): Double {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val length = hypot(dx, dz)
    if (length <= 1e-9) return 0.0
    return ((x - from.x) * dx + (z - from.z) * dz) / length
}

/**
 * The corridor follower with the terminal machinery removed: steer, hold forward, and
 * press one optional launch.
 *
 * A transition between two motion anchors never brakes and never approaches the goal —
 * stopping is [CorridorFollowerProgram]'s job, run once on the final suffix. Keeping the
 * steering law byte-identical to that program's is what lets an anchor prefix and a
 * terminal suffix be concatenated into one replayable tape.
 */
internal class SegmentFollowerProgram(
    private val nodes: List<HorizontalPoint>,
    startProgress: Int,
    private val sprint: Boolean,
    private val lookAheadNodes: Int,
    private val launch: LaunchTrigger?,
    private val maxYawChange: Double,
    private val easeTurns: Boolean = false,
) : ControlProgram {
    private var progressIndex = startProgress

    override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
        advanceProgress(observed)
        val jump = launch?.press(observed) == true

        val target = nodes[minOf(nodes.lastIndex, progressIndex + lookAheadNodes)]
        val desiredYaw = Math.toDegrees(atan2(target.z - observed.position.z, target.x - observed.position.x)) - 90.0
        val yawError = Rotation.wrap(desiredYaw - observed.rotation.yaw)
        val yawDelta = yawError.coerceIn(-maxYawChange, maxYawChange)
        // Easing a turn is releasing forward while the heading catches up, not braking:
        // a body pinned at forward=1.0 through a corner drives its own width into the
        // outside wall, which is the "harsh planned turn" the field logs kept reporting.
        // Offered as an action rather than imposed, so the search pays for it only where
        // the straight line genuinely fails.
        val forward = if (easeTurns && abs(yawError) > EASE_TURN_DEGREES) 0.0 else 1.0
        return MovementSimulationInput(
            forward = forward,
            sprint = sprint,
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

    private fun horizontalDistanceSquared(node: HorizontalPoint, observed: MovementSimulationState): Double {
        val dx = node.x - observed.position.x
        val dz = node.z - observed.position.z
        return dx * dx + dz * dz
    }
}
