package com.lambda.pathing

import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.util.player.prediction.MovementSimulationInput

internal class Walk(val request: PathingRequest) {
    var planningSession: PlanningSession? = null

    var leg = 0

    var planningYaw: Double? = null

    var pendingPath: PublishedPath? = null
    var alignmentTicks = 0
    var settleTicks = 0
    var cursor: TrajectoryExecutionCursor? = null

    var tickInput: MovementSimulationInput? = null
    var awaitingObservation = false

    var pendingImprovement: PublishedPath? = null

    var pendingNextLeg: PublishedPath? = null

    var handoffBaseFrames = 0

    var pipelinedTape: Long? = null

    var clearedDemotions = false

    fun cancelPlanning() {
        val planning = planningSession
        planningSession = null
        planning?.cancel()
    }

    fun resetForReplan() {
        cancelPlanning()
        cursor = null
        awaitingObservation = false
        pendingPath = null
        pendingImprovement = null
        pendingNextLeg = null
        handoffBaseFrames = 0
        pipelinedTape = null
        planningYaw = null
        alignmentTicks = 0
        settleTicks = 0
    }
}
