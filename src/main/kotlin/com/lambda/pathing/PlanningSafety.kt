/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

/** Cooperative cancellation shared by the client-thread session and planning worker. */
internal class PlanningCancellation {
    private val cancelled = AtomicBoolean()

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
    }
}

/** Runtime ownership for one worker generation. All methods are safe across the client and worker threads. */
internal class PlanningSession(
    val generation: Long,
    val request: PathingRequest,
    val snapshotRevision: Long,
) {
    val cancellation = PlanningCancellation()

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
