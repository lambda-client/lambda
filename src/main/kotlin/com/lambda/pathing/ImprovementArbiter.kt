package com.lambda.pathing

import com.lambda.pathing.trajectory.PublishedPath

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
            // A backtrack swap: a shallower anchor on a better line. Guide values drift
            // as the field expands, so arrivals recorded at different publish times are
            // not comparable -- comparing them was tried and lied. The publication
            // therefore carries both arrivals computed in the same instant against the
            // same field, plus which running tape it compared; only that exact tape may
            // be displaced by it, and only for the same gain the search's own gate
            // demanded.
            val comparable = offered.comparedRunningSequence == running.publicationSequence.toLong() &&
                offered.arrivalTicksEstimate.isFinite() && offered.comparedRunningArrivalTicks.isFinite()
            val gains = comparable &&
                offered.arrivalTicksEstimate + SWAP_GAIN_TICKS <= offered.comparedRunningArrivalTicks
            // The publication gate enforces the same floor against the cursor it saw;
            // this one holds against the cursor at delivery, which has moved since.
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

    /** Mirrors the search's REFINEMENT_GAIN_TICKS: adopting a swap must buy what its publication gate demanded. */
    private const val SWAP_GAIN_TICKS = 3.0

    /** A commit chunk: the certified runway a swap must still hand the body on arrival. */
    private const val SWAP_MIN_RUNWAY_FRAMES = 20
}
