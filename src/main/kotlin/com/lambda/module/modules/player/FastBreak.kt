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

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import java.util.concurrent.ConcurrentLinkedQueue

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    tag = ModuleTag.PLAYER,
) {
    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

    init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllGroupsExcept(breakConfig, rotationConfig, hotbarConfig)
				buildConfig.apply {
					editTyped(
						::pathing,
						::stayInRange,
						::useDefaultReach,
						::checkSideVisibility,
						::strictRayCast
					) { defaultValue(false) }
					::interactionsPerTick.edit { defaultValue(1) }
					::interactReach.edit { defaultValue(Double.MAX_VALUE) }
				}
				breakConfig.apply {
					editTyped(
						::avoidLiquids,
						::avoidSupporting,
						::efficientOnly,
						::suitableToolsOnly
					) { defaultValue(false) }
					editTyped(
						::rotate,
						::doubleBreak
					) { defaultValue(false); hide() }
					::breaksPerTick.edit { defaultValue(1); hide() }
					::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Input.Post)); hide() }
					::maxPendingBreaks.edit { defaultValue(Int.MAX_VALUE); hide() }
					hide(::sorter, ::unsafeCancels)
				}
				hotbarConfig::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Input.Post)); hide() }
			}
		}

        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()
	        runSafeAutomated {
				breakRequest(listOf(event.pos), pendingInteractions)?.submit()
			}
        }
    }
}
