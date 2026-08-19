/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import kotlin.math.hypot
import kotlin.math.sqrt

/** A route node's walkable centre; the continuous point the controllers steer at. */
internal data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

internal fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

internal fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
    hypot(to.x - from.x, to.z - from.z)

internal fun spliceDistanceSquared(node: HorizontalPoint, state: MovementSimulationState): Double {
    val dx = state.position.x - node.x
    val dy = state.position.y - node.y
    val dz = state.position.z - node.z
    return dx * dx + dy * dy + dz * dz
}

internal fun horizontalDistanceToPolyline(x: Double, z: Double, nodes: List<HorizontalPoint>): Double {
    if (nodes.size == 1) return hypot(x - nodes[0].x, z - nodes[0].z)
    var bestSquared = Double.POSITIVE_INFINITY
    for (index in 0 until nodes.lastIndex) {
        val a = nodes[index]
        val b = nodes[index + 1]
        val dx = b.x - a.x
        val dz = b.z - a.z
        val lengthSquared = dx * dx + dz * dz
        val projection = if (lengthSquared == 0.0) 0.0 else
            (((x - a.x) * dx + (z - a.z) * dz) / lengthSquared).coerceIn(0.0, 1.0)
        val ex = x - (a.x + projection * dx)
        val ez = z - (a.z + projection * dz)
        bestSquared = minOf(bestSquared, ex * ex + ez * ez)
    }
    return sqrt(bestSquared)
}

/**
 * Monotone local route progress, advanced one state at a time.
 *
 * Local because a route that doubles back passes close to its own earlier nodes; a
 * global nearest-node search would snap across the fold. The same rule the corridor
 * controller steers by, so search-side attribution and control never disagree.
 */
internal class RouteProgressTracker(
    private val nodes: List<HorizontalPoint>,
    startProgress: Int = 0,
) {
    var progress: Int = startProgress
        private set

    fun advance(state: MovementSimulationState): Int {
        val limit = minOf(nodes.lastIndex, progress + MAX_PROGRESS_ADVANCE)
        var best = progress
        var bestSquared = spliceDistanceSquared(nodes[progress], state)
        for (index in progress + 1..limit) {
            val squared = spliceDistanceSquared(nodes[index], state)
            if (squared < bestSquared) {
                best = index
                bestSquared = squared
            }
        }
        progress = best
        return progress
    }
}

/**
 * Same monotone local progress rule as the controller; never snap across a folded route.
 *
 * [throughFrame] may name a frame that was never recorded: a rejected step (lava, an
 * uncaptured block) reports the frame it *died on*, and the rollout stops one short of
 * it. Progress through a frame that does not exist is progress through the last one
 * that does.
 */
internal fun routeProgress(
    rollout: TrajectoryRollout,
    throughFrame: Int,
    nodes: List<HorizontalPoint>,
): Int = routeProgressByFrame(rollout, throughFrame, nodes).lastOrNull() ?: 0

/** Progress at every frame through [throughFrame] in one monotone forward scan. */
internal fun routeProgressByFrame(
    rollout: TrajectoryRollout,
    throughFrame: Int,
    nodes: List<HorizontalPoint>,
): IntArray {
    val last = minOf(throughFrame, rollout.frames.lastIndex)
    val progressByFrame = IntArray(maxOf(last + 1, 0))
    val tracker = RouteProgressTracker(nodes)
    for (frame in 0..last) {
        progressByFrame[frame] = tracker.advance(rollout.frames[frame].state)
    }
    return progressByFrame
}

/** Compass bearing from one point to another, in the same frame the controllers steer in. */
internal fun bearingBetween(from: HorizontalPoint, to: HorizontalPoint): Double =
    Math.toDegrees(kotlin.math.atan2(to.z - from.z, to.x - from.x)) - 90.0

/** A tick advances well under one block, so two nodes is generous headroom. */
internal const val MAX_PROGRESS_ADVANCE = 2

/** Contacts, not frames spent sliding along one obstacle. */
internal fun collisionEvents(
    entry: MovementSimulationState,
    frames: List<SimulatedTrajectoryFrame>,
): Int {
    var previous = entry.horizontalCollision
    var events = 0
    for (frame in frames) {
        if (frame.state.horizontalCollision && !previous) events++
        previous = frame.state.horizontalCollision
    }
    return events
}

internal fun inputSwitches(
    entryInput: MovementSimulationInput?,
    frames: List<SimulatedTrajectoryFrame>,
): Int {
    var previous = entryInput
    var switches = 0
    for (frame in frames) {
        if (previous != null && previous != frame.input) switches++
        previous = frame.input
    }
    return switches
}

/**
 * Runway a transition's launch left before the hazard a walk from the same entry ran
 * into. A couple of frames is real safety against clipping the lip; more is noise, and
 * unbounded margin made the ranking race to the earliest certifiable frame — the
 * reported "jumps too early".
 */
internal fun launchMargin(
    frames: List<SimulatedTrajectoryFrame>,
    hazardFrame: Int?,
    cap: Int = LAUNCH_MARGIN_FRAME_CAP,
): Int {
    val hazard = hazardFrame ?: return 0
    val launch = frames.firstOrNull { it.input.jump }?.index ?: return 0
    return (hazard - launch).coerceIn(0, cap)
}

/**
 * The frame a launch would have to beat, or null if no launch could help.
 * `UnsupportedPhysics` counts: a body walking off a ledge into lava is falling, and to a
 * solver that means the same thing — it needed to leave the ground here. `OutsideSnapshot`
 * never counts: that is our capture being too small, not a hazard a jump can clear.
 */
internal fun launchSeedFrame(diagnostic: TrajectoryDiagnostic): Int? = when (diagnostic) {
    is TrajectoryDiagnostic.FellBelowRoute,
    is TrajectoryDiagnostic.HorizontalCollision,
    is TrajectoryDiagnostic.UnsupportedPhysics -> diagnostic.frame

    else -> null
}

/** Roughly one stride of pre-hazard runway; earlier trades landing depth for nothing. */
internal const val LAUNCH_MARGIN_FRAME_CAP = 3
