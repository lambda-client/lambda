package com.lambda.pathing.movement

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MAX_PROGRESS_ADVANCE
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import kotlin.math.atan2

internal class PursuitTracker(val nodes: List<HorizontalPoint>) {
    var progressIndex = 0
        private set

    fun advance(observed: MovementSimulationState) {
        val limit = minOf(nodes.lastIndex, progressIndex + MAX_PROGRESS_ADVANCE)
        var best = progressIndex
        var bestSquared = distanceSquared(nodes[progressIndex], observed)
        for (index in progressIndex + 1..limit) {
            val squared = distanceSquared(nodes[index], observed)
            if (squared < bestSquared) {
                bestSquared = squared
                best = index
            }
        }
        progressIndex = best
    }

    fun target(lookAheadNodes: Int): HorizontalPoint =
        nodes[minOf(nodes.lastIndex, progressIndex + lookAheadNodes)]

    private fun distanceSquared(node: HorizontalPoint, observed: MovementSimulationState): Double {
        val dx = node.x - observed.position.x
        val dz = node.z - observed.position.z
        return dx * dx + dz * dz
    }

    companion object {
        /** Default nodes of lead the pursuit target sits ahead of the tracked progress. */
        internal const val DEFAULT_LOOK_AHEAD_NODES = 1
    }
}

internal fun yawTowards(
    target: HorizontalPoint,
    observed: MovementSimulationState,
): Double = Math.toDegrees(
    atan2(target.z - observed.position.z, target.x - observed.position.x)
) - 90.0

internal fun yawStepTowards(
    desiredYaw: Double,
    observed: MovementSimulationState,
    maxYawChange: Double,
): Double = Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
