package com.lambda.pathing.execution

import com.lambda.pathing.PathingRequest
import com.lambda.pathing.session.PlanningSession
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
     * The next leg, planned from the running tape's terminal while the body is still on it
     * and installed on arrival. See docs/decisions/publication-protocol.md.
     */
    var successorSession: PlanningSession? = null
    var successorPath: PublishedPath? = null

    /** Frames each adoption added to the tape, and the ticks the search took to find them. */
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

    /**
     * Frames adopted against ticks spent: the body eats one frame per tick, so a deficit
     * here is paid at the next brake. See docs/decisions/session-loop.md.
     */
    fun publicationCadence(): String {
        if (adoptionGains.isEmpty()) return "no adoptions"
        val frames = adoptionGains.sum()
        val millis = adoptionMillis.sum().coerceAtLeast(1L)
        return "%d adoption(s) added %d frames over %d ms (%.1f frames/s produced vs %d consumed)"
            .format(adoptionGains.size, frames, millis, frames * 1000.0 / millis, TICKS_PER_SECOND)
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

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
