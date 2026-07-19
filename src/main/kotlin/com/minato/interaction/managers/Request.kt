
package com.minato.interaction.managers

import com.minato.context.Automated
import com.minato.event.events.TickEvent

/**
 * A simple format to ensure basic requirements and information when requesting a manager.
 *
 * @property requestId Used for tracking how many requests there have been and what number this request is.
 * @property fresh If this request is new.
 * @property done If this request has been completed.
 */
abstract class Request : Automated {
	abstract val requestId: Int
	var fresh = true

	abstract val tickStageMask: Collection<TickEvent>
	abstract val nowOrNothing: Boolean

	abstract val done: Boolean

	abstract fun submit(queueIfMismatchedStage: Boolean = true): Request
}
