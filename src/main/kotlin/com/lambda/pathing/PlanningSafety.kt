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
    /** Created for the leg after a running tape, rooted at its settled terminal. */
    val pipelined: Boolean = false,
) {
    val cancellation = PlanningCancellation()

    /**
     * True while this session searches the leg *after* the running tape, rooted at that
     * tape's certified terminal stance. A parked session's publications are held for the
     * hand-off instead of spliced into a tape they do not extend, and it reports no
     * execution cursor -- the frames the manager feeds it belong to the running tape,
     * not to the leg this session is searching.
     */
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
