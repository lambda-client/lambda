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

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.threading.runSafe

/**
 * This class handles requests, offering specific opening times, and an option to queue a request for the
 * next opening if closed
 */
abstract class RequestHandler<R : Request>(
    vararg openStages: Event,
    private val onOpen: (SafeContext.() -> Unit)? = null,
    private val onClose: (SafeContext.() -> Unit)? = null
) {
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
     * Represents if the handler performed any external actions within this tick
     */
    var activeThisTick = false; protected set

    init {
        openStages.forEach {
            when (it) {
                is TickEvent.Pre -> openRequestsFor(it)
                is TickEvent.Post -> openRequestsFor(it)
                is TickEvent.Network.Pre -> openRequestsFor(it)
                is TickEvent.Network.Post -> openRequestsFor(it)
                is TickEvent.Input.Pre -> openRequestsFor(it)
                is TickEvent.Input.Post -> openRequestsFor(it)
                is TickEvent.WorldRender.Pre -> openRequestsFor(it)
                is TickEvent.WorldRender.Post -> openRequestsFor(it)
                is TickEvent.Sound.Pre -> openRequestsFor(it)
                is TickEvent.Sound.Post -> openRequestsFor(it)
                is TickEvent.Render.Pre -> openRequestsFor(it)
                is TickEvent.Render.Post -> openRequestsFor(it)
                is TickEvent.Player.Pre -> openRequestsFor(it)
                is TickEvent.Player.Post -> openRequestsFor(it)
                else -> throw IllegalArgumentException("Event '$it' is not allowed for requests")
            }
        }

        listen<TickEvent.Post>(Int.MIN_VALUE) {
            activeThisTick = false
        }
    }

    /**
     * opens the handler for requests for the duration of the given event
     */
    private inline fun <reified T : Event> openRequestsFor(stage: T) {
        listen<T>(priority = Int.MAX_VALUE) {
            tickStage = stage
            queuedRequest?.let { request ->
                handleRequest(request)
                request.fresh = false
                queuedRequest = null
            }
            acceptingRequests = true
            onOpen?.invoke(this)
            preEvent()
        }
        listen<T>(priority = Int.MIN_VALUE) {
            onClose?.invoke(this)
            acceptingRequests = false
        }
    }

    /**
     * Registers a new request
     *
     * @param request The request to register.
     * @param queueIfClosed queues the request for the next time the handlers accepting requests
     * @return The registered request.
     */
    fun request(request: R, queueIfClosed: Boolean = true): R {
        if (!acceptingRequests) {
            val canOverrideQueued = queuedRequest?.run { config === request.config } ?: true
            if (queueIfClosed && canOverrideQueued) {
                queuedRequest = request
            }
            return request
        }

        runSafe {
            handleRequest(request)
            request.fresh = false
        }
        return request
    }

    /**
     * Handles a request
     */
    abstract fun SafeContext.handleRequest(request: R)

    protected abstract fun preEvent(): Event
}
