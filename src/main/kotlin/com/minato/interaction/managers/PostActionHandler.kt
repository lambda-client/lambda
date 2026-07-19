
package com.minato.interaction.managers

import com.minato.context.Automated
import com.minato.event.events.ConnectionEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.interaction.handlers.breaking.BrokenBlockHandler
import com.minato.util.collections.LimitedDecayQueue

/**
 * A simple interface for handlers of actions that need some sort of server response after being executed.
 */
abstract class PostActionHandler<T : ActionInfo> {
	abstract val pendingActions: LimitedDecayQueue<T>

    init {
        listen<TickEvent.Pre>({ Int.MAX_VALUE }) {
            pendingActions.cleanUp()
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
            pendingActions.clear()
        }
    }

	fun T.startPending() {
		pendingActions.add(this)
		pendingInteractionsList.add(context)
	}

	fun T.stopPending() {
		pendingActions.remove(this)
		pendingInteractionsList.remove(context)
	}

	fun Automated.setPendingConfigs() {
		BrokenBlockHandler.pendingActions.setSizeLimit(buildConfig.maxPendingActions)
		BrokenBlockHandler.pendingActions.setDecayTime(buildConfig.actionTimeout * 50L)
	}
}