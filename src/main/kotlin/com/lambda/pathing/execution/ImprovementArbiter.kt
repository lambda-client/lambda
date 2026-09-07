package com.lambda.pathing.execution

import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.trajectory.SWAP_FLOOR_GAIN_TICKS

internal object ImprovementArbiter {
    sealed interface Verdict {
        data object BeginFresh : Verdict

        data class Keep(val reason: String) : Verdict

        data object DeferForObservation : Verdict

        data class Adopt(val frame: Int) : Verdict
    }

    fun judge(
        running: PublishedPath?,
        cursorFrame: Int?,
        awaitingObservation: Boolean,
        offered: PublishedPath,
    ): Verdict {
        if (running == null || cursorFrame == null) return Verdict.BeginFresh
        if (offered.planningGeneration != running.planningGeneration) {
            return Verdict.Keep("publication belongs to a different planning generation")
        }
        if (offered.publicationSequence <= running.publicationSequence) {
            return Verdict.Keep("publication is older than the running tape")
        }
        if (awaitingObservation) return Verdict.DeferForObservation

        if (cursorFrame > offered.plan.tape.frameCount) {
            return Verdict.Keep("improvement is shorter than the walk so far")
        }
        if (!running.partial && !offered.partial &&
            offered.plan.tape.frameCount >= running.plan.tape.frameCount
        ) {
            return Verdict.Keep("improvement is not shorter than the running tape")
        }
        if (running.partial && offered.partial && offered.safeAnchorFrame <= running.safeAnchorFrame) {
            // A backtrack swap: only the exact running tape the publication compared
            // against (same-instant arrivals) may be displaced, and only for the swap
            // floor. See docs/decisions/publication-protocol.md.
            val comparable = offered.comparedRunningSequence == running.publicationSequence.toLong() &&
                offered.arrivalTicksEstimate.isFinite() && offered.comparedRunningArrivalTicks.isFinite()
            val gains = comparable &&
                offered.arrivalTicksEstimate + SWAP_GAIN_TICKS <= offered.comparedRunningArrivalTicks
            // Re-checked against the cursor at delivery, which has moved since the gate saw it.
            val runway = offered.safeAnchorFrame - cursorFrame >= SWAP_MIN_RUNWAY_FRAMES
            if (!gains || !runway) return Verdict.Keep("partial publication commits no new anchor")
        }
        if (offered.partial && offered.safeAnchorFrame <= cursorFrame) {
            return Verdict.Keep("partial publication has no unexecuted progress anchor")
        }
        val diverges = (0 until cursorFrame).any { running.plan.tape[it] != offered.plan.tape[it] }
        if (diverges) return Verdict.Keep("improvement diverges behind the cursor")

        return Verdict.Adopt(cursorFrame)
    }

    /** Adopting a swap must buy what its publication gate demanded; see docs/decisions/swap-floor.md. */
    private const val SWAP_GAIN_TICKS = SWAP_FLOOR_GAIN_TICKS

    /** A commit chunk: the certified runway a swap must still hand the body on arrival. */
    private const val SWAP_MIN_RUNWAY_FRAMES = 20
}
