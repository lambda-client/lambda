
package com.minato.interaction.handlers.interacting

import com.minato.config.automation.AutomationConfig
import com.minato.config.blocks.InteractConfig
import com.minato.event.events.WorldEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.PostActionHandler
import com.minato.interaction.managers.interacting.InteractInfo
import com.minato.interaction.managers.interacting.InteractManager.placeSound
import com.minato.module.modules.client.Client
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.matches
import com.minato.util.CommunicationUtils.warn
import com.minato.util.collections.LimitedDecayQueue

object InteractedBlockHandler : PostActionHandler<InteractInfo>() {
	override val pendingActions = LimitedDecayQueue<InteractInfo>(
		AutomationConfig.DEFAULT.buildConfig.maxPendingActions,
		AutomationConfig.DEFAULT.buildConfig.actionTimeout * 50L
	) {
		if (Client.verboseDebug) warn("${it::class.simpleName} at ${it.context.blockPos.toShortString()} timed out")
		if (it.interactConfig.interactConfirmationMode != InteractConfig.InteractConfirmationMode.AwaitThenPlace) {
			runSafe {
				world.setBlockState(it.context.blockPos, it.context.cachedState)
			}
		}
		it.pendingInteractionsList.remove(it.context)
	}

    init {
        listen<WorldEvent.BlockUpdate.Server>({ Int.MIN_VALUE }) { event ->
            pendingActions
                .firstOrNull { it.context.blockPos == event.pos }
                ?.let { pending ->
                    if (!pending.context.expectedState.matches(event.newState)) {
                        if (pending.context.cachedState.matches(event.newState, pending.context.preProcessingInfo.ignore)) {
                            pending.context.cachedState = event.newState
                            return@listen
                        }

						pending.stopPending()

						if (Client.verboseDebug) this@InteractedBlockHandler.warn("Placed block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.expectedState}")
						return@listen
					}

					pending.stopPending()

					if (pending.interactConfig.interactConfirmationMode == InteractConfig.InteractConfirmationMode.AwaitThenPlace)
						with(pending.context) { placeSound(expectedState, blockPos) }
					pending.onPlace?.invoke(this, pending.context.blockPos)
				}
		}
	}
}