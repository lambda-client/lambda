package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState

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
