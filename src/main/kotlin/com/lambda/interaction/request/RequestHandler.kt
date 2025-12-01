/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.Event
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.ManagerUtils.accumulatedManagerPriority
import com.lambda.threading.runSafeAutomated
import kotlin.reflect.KClass

/**
 * This class handles requests, offering specific opening times, and an option to queue a request for the
 * next opening if closed
 */
abstract class RequestHandler<R : Request>(
    val stagePriority: Int,
    vararg val blacklistedStages: TickEvent,
    private val onOpen: (SafeContext.() -> Unit)? = null,
    private val onClose: (SafeContext.() -> Unit)? = null
) : Loadable {
	val openStages: List<TickEvent> = ALL_STAGES.filter { it !in blacklistedStages }

    /**
     * Represents if the handler is accepting requests at any given time
     */
    private var acceptingRequests = false

    /**
     * Represents the sequence stage the current tick is at
     */
    var tickStage: Event? = null; private set

    /**
     * If a request is made while the handler isn't accepting requests, it is placed into [queuedRequest] and run
     * at the start of the next open request timeframe
     */
    var queuedRequest: R? = null; protected set

    /**
     * Represents if the handler performed any actions within this tick
     */
    var activeThisTick = false; protected set

    override fun load(): String {
        openStages.forEach { openRequestsFor(it::class, it) }

        listen<TickEvent.Post>(Int.MIN_VALUE) {
            activeThisTick = false
            queuedRequest = null
        }

        return super.load()
    }

    /**
     * opens the handler for requests for the duration of the given event
     */
    private inline fun <reified T : Event> openRequestsFor(instance: KClass<out T>, stage: T) {
        listen(instance, priority = (Int.MAX_VALUE - 1) - (accumulatedManagerPriority - stagePriority)) {
            tickStage = stage
            queuedRequest?.let { request ->
                if (tickStage !in request.tickStageMask) return@let
                request.runSafeAutomated { handleRequest(request) }
                request.fresh = false
                queuedRequest = null
            }
            acceptingRequests = true
            onOpen?.invoke(this)
            preEvent()
        }

        listen(instance, priority = (Int.MIN_VALUE + 1) + stagePriority) {
            onClose?.invoke(this)
            acceptingRequests = false
        }
    }

    /**
     * Registers a new request
     *
     * @param request The request to register.
     * @param queueIfMismatchedStage queues the request for the next time the handlers accepting requests
     * @return The registered request.
     */
    fun request(request: R, queueIfMismatchedStage: Boolean = true): R {
        val canOverrideQueued = queuedRequest?.let { it as Automated === request as Automated } != false
        if (!canOverrideQueued) return request
        if ((!acceptingRequests || tickStage !in request.tickStageMask)) {
            if (!queueIfMismatchedStage || request.nowOrNothing) return request
            val currentStageIndex = ALL_STAGES.indexOf(tickStage)
            if (openStages.none { ALL_STAGES.indexOf(it) > currentStageIndex && it in request.tickStageMask })
                return request
            queuedRequest = request
            return request
        }

        request.runSafeAutomated {
            handleRequest(request)
            request.fresh = false
        }
        return request
    }

    /**
     * Handles a request
     */
    abstract fun AutomatedSafeContext.handleRequest(request: R)

    protected abstract fun preEvent(): Event
}
