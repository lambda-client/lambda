package com.lambda.pathing

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface PlanningFailure {
    val message: String

    data class InvalidRequest(override val message: String) : PlanningFailure
    data class ResourceLimit(override val message: String) : PlanningFailure
    data class WorldUnavailable(override val message: String) : PlanningFailure
    data class NoRoute(override val message: String) : PlanningFailure

    data class NoCertifiedMotion(override val message: String) : PlanningFailure
}

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

    val pipelined: Boolean = false,
) {
    val cancellation = PlanningCancellation()

    @Volatile
    var parked: Boolean = false

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

    fun executionFrame(): Int? =
        if (parked) null else executionFrame.get().takeUnless { it == NOT_EXECUTING }

    fun cancel() {
        cancellation.cancel()
        future?.cancel(true)
    }

    private companion object {
        const val NOT_EXECUTING = -1
    }
}
