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

internal fun launchMargin(
    frames: List<SimulatedTrajectoryFrame>,
    hazardFrame: Int?,
    cap: Int = LAUNCH_MARGIN_FRAME_CAP,
): Int {
    val hazard = hazardFrame ?: return 0
    val launch = frames.firstOrNull { it.input.jump }?.index ?: return 0
    return (hazard - launch).coerceIn(0, cap)
}

internal fun launchSeedFrame(diagnostic: TrajectoryDiagnostic): Int? = when (diagnostic) {
    is TrajectoryDiagnostic.FellBelowRoute,
    is TrajectoryDiagnostic.HorizontalCollision,
    is TrajectoryDiagnostic.UnsupportedPhysics -> diagnostic.frame

    else -> null
}

internal const val LAUNCH_MARGIN_FRAME_CAP = 3
