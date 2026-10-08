package com.lambda.pathing.execution

import com.lambda.pathing.search.PublishedPath

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
		runningInvalid: Boolean = false,
	): Verdict {
		if (running == null || cursorFrame == null) return Verdict.BeginFresh
		if (offered.publicationSequence <= running.publicationSequence) {
			return Verdict.Keep("publication is older than the running tape")
		}
		if (awaitingObservation) return Verdict.DeferForObservation

		if (cursorFrame > offered.plan.tape.frameCount) {
			return Verdict.Keep("improvement is shorter than the walk so far")
		}
		if (!runningInvalid && offered.partial && offered.safeAnchorFrame <= cursorFrame) {
			return Verdict.Keep("partial publication has no unexecuted progress anchor")
		}
		if (divergesBehind(running, offered, cursorFrame)) {
			return Verdict.Keep(if (runningInvalid) "repair diverges behind the cursor" else "improvement diverges behind the cursor")
		}
		return Verdict.Adopt(cursorFrame)
	}

	private fun divergesBehind(running: PublishedPath, offered: PublishedPath, cursorFrame: Int): Boolean =
		(0 until cursorFrame).any { running.plan.tape[it] != offered.plan.tape[it] }
}
