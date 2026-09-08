package com.lambda.pathing.session

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.PathingRequest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class PlanningCancellation {
	private val cancelled = AtomicBoolean()

	val isCancelled: Boolean get() = cancelled.get()

	fun cancel() {
		cancelled.set(true)
	}
}

internal class PlanningSession(
	val generation: Long,
	val request: PathingRequest,
) {
	val cancellation = PlanningCancellation()

	/** World revision when the session launched; a retry is only worth it once this has moved. */
	@Volatile
	var launchRevision: Long = -1L

	/**
	 * Newest publication sequence the executor has installed or adopted. The worker
	 * may not publish past an unacknowledged tape: the acknowledgement guarantees the
	 * running tape and the worker's published tip agree before the search speculates
	 * beyond the published brake.
	 */
	@Volatile
	var adoptedSequence: Long = 0L

	private val executionFrame = AtomicInteger(NOT_EXECUTING)

	@Volatile
	private var future: CompletableFuture<PathPlanResult>? = null

	fun attach(future: CompletableFuture<PathPlanResult>) {
		this.future = future
		if (cancellation.isCancelled) future.cancel(true)
	}

	fun updateExecutionFrame(frame: Int?) {
		executionFrame.set(frame ?: NOT_EXECUTING)
	}

	fun executionFrame(): Int? = executionFrame.get().takeUnless { it == NOT_EXECUTING }

	fun cancel() {
		cancellation.cancel()
		future?.cancel(true)
	}

	private companion object {
		const val NOT_EXECUTING = -1
	}
}
