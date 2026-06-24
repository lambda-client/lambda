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

package com.lambda.interaction.handlers.interacting

import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.InteractConfig
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.PostActionHandler
import com.lambda.interaction.managers.interacting.InteractInfo
import com.lambda.interaction.managers.interacting.InteractManager.placeSound
import com.lambda.module.modules.client.Client
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.matches
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.collections.LimitedDecayQueue

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