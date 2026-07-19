
package com.minato.interaction.managers

import com.minato.context.Automated
import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.core.Loadable
import com.minato.event.Event
import com.minato.event.events.TickEvent
import com.minato.event.events.TickEvent.Companion.ALL_STAGES
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.ManagerUtils.accumulatedManagerPriority
import com.minato.threading.runSafeAutomated
import kotlin.reflect.KClass

/**
 * This class handles requests, offering specific opening times, and an option to queue a request for the
 * next opening if closed
 */
abstract class Manager<R : Request>(
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

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            activeThisTick = false
            queuedRequest = null
        }

		return super.load()
	}

    /**
     * opens the handler for requests for the duration of the given event
     */
    private inline fun <reified T : Event> openRequestsFor(instance: KClass<out T>, stage: T) {
        listen(instance, { (Int.MAX_VALUE - 1) - (accumulatedManagerPriority - stagePriority) }) {
            tickStage = stage
            queuedRequest?.let { request ->
                if (tickStage !in request.tickStageMask) return@let
                request.runSafeAutomated { handleRequest(request) }
                request.fresh = false
                queuedRequest = null
            }
            acceptingRequests = true
            onOpen?.invoke(this)
        }

        listen(instance, { (Int.MIN_VALUE + 1) + stagePriority }) {
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
		if (!acceptingRequests || tickStage !in request.tickStageMask) {
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
}
