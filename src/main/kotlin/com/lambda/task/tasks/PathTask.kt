/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.task.tasks

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingSession.State as PathState
import com.lambda.task.Task

/**
 * Walks [waypoints] in order through [PathingService] and completes when the body has
 * arrived at the last one; fails when a leg fails. Cancelling the task cancels the route.
 */
class PathTask @Ta5kBuilder constructor(
    val waypoints: List<Stance>,
    automated: Automated,
) : Task<Unit>(), Automated by automated {
    override val name: String
        get() = waypoints.lastOrNull()?.let { "Pathing to (${it.x}, ${it.y}, ${it.z})" } ?: "Pathing nowhere"

    /** The first leg's request; the route has taken effect once it is no longer fresh. */
    private var request: PathingRequest? = null

    override fun SafeContext.onStart() {
        request = PathingService.route(this@PathTask, waypoints)
            ?: return failure("no waypoints to walk")
    }

    override fun SafeContext.onCancel() {
        PathingService.cancel()
    }

    init {
        listen<TickEvent.Post> {
            val first = request ?: return@listen
            // Until the manager has handled the first leg the status still describes an
            // earlier walk; a stale Complete there is not this route's arrival.
            if (first.fresh) return@listen
            when (val status = PathingService.status) {
                is PathState.Failed -> failure(status.reason)
                is PathState.Complete -> if (!PathingService.isRouting) success()
                else -> Unit
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun Automated.pathTo(waypoints: List<Stance>) = PathTask(waypoints, this)

        @Ta5kBuilder
        fun Automated.pathTo(goal: Stance) = PathTask(listOf(goal), this)
    }
}
