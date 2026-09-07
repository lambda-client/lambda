package com.lambda.pathing.api

import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry

/**
 * The one surface commands, tasks, the renderer and the test harness use to drive and
 * observe the planner. Everything here forwards to [PathingManager], which owns the
 * session; [status] and [telemetry] are safe to read from any thread.
 */
object PathingService {
    /**
     * Walks [waypoints] in order: the first leg is requested now and each arrival submits
     * the next. Any unrelated pathing request, a cancel, or a failed leg drops the
     * remainder. Returns the first leg's request, or null when there were no waypoints.
     */
    fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? =
        PathingManager.route(automated, waypoints)

    /** Stops the walk and drops queued waypoints; the published tape and telemetry stay for the renderer. */
    fun cancel() = PathingManager.cancel()

    /** [cancel] plus forgetting the published tape, the telemetry and the retained journey. */
    fun clear() = PathingManager.clear()

    val status: State get() = PathingManager.status

    val telemetry: Telemetry get() = PathingManager.telemetry

    /** True while a leg is being walked or a further leg (or request) is still queued. */
    val isRouting: Boolean get() = PathingManager.isRouting

    val renderConfig: PathingRenderConfig get() = PathingManager.renderConfig

    fun diagnostics(): String = PathingManager.diagnostics()
}
