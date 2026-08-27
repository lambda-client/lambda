package com.lambda.pathing

import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.prediction.simulation.MovementSimulationInput

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

    var holding = false
    var holds = 0

    var sessionRestarts = 0
    var sessionFailure: String? = null

    /**
     * The next leg, planned from the running tape's terminal while the body is still on it.
     *
     * A session that dead-ends mid-walk used to leave the body to drain its tape, stop,
     * and only then start a fresh search. The successor removes the search from that
     * critical path -- it is planned during replay and installed on arrival.
     */
    var successorSession: PlanningSession? = null
    var successorPath: PublishedPath? = null

    /**
     * Frames each adoption added to the tape, and how long the search took to find them.
     *
     * Together these are the walk's throughput. The body consumes one frame per tick no
     * matter what, so an adoption that adds fewer frames than the ticks it cost is one
     * the walk runs a deficit on, and the deficit is paid at the next brake -- which is
     * what a mid-route stop actually is.
     */
    val adoptionGains = ArrayList<Int>()
    val adoptionMillis = ArrayList<Long>()

    private var lastAdoptionMillis = System.currentTimeMillis()
    private var lastAdoptedFrames = 0

    fun recordAdoption(tapeFrames: Int) {
        val now = System.currentTimeMillis()
        adoptionGains += (tapeFrames - lastAdoptedFrames).coerceAtLeast(0)
        adoptionMillis += (now - lastAdoptionMillis).coerceAtLeast(0)
        lastAdoptedFrames = tapeFrames
        lastAdoptionMillis = now
    }

    fun cancelPlanning() {
        val planning = planningSession
        planningSession = null
        planning?.cancel()
    }

    fun cancelSuccessor() {
        val successor = successorSession
        successorSession = null
        successorPath = null
        successor?.cancel()
    }

    fun resetForReplan() {
        cancelPlanning()
        cancelSuccessor()
        cursor = null
        awaitingObservation = false
        pendingPath = null
        pendingImprovement = null
        holding = false
        sessionFailure = null
        planningYaw = null
        alignmentTicks = 0
        settleTicks = 0
    }
}
