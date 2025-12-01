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
import com.lambda.config.groups.BuildConfig
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.results.BreakResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.interaction.request.hotbar.HotbarConfig
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

	override val buildConfig = object : BuildConfig by super.buildConfig {
		override val pathing = false
		override val stayInRange = false
		override val interactionsPerTick = 1
		override val useDefaultReach = false
		override val interactReach = Double.MAX_VALUE
		override val checkSideVisibility = false
		override val strictRayCast = false
	}

    override val breakConfig = object : BreakConfig by super.breakConfig {
        override val rotateForBreak = false
        override val doubleBreak = false
        override val breaksPerTick = 1
	    override val tickStageMask = setOf(TickEvent.Input.Post)
	    override val maxPendingBreaks = Int.MAX_VALUE
    }

	override val hotbarConfig = object : HotbarConfig by super.hotbarConfig {
		override val tickStageMask = setOf(TickEvent.Input.Post)
	}

    init {
        setDefaultAutomationConfig {
			applyEdits {
				breakConfig.apply {
					editTyped(
						::avoidLiquids,
						::avoidSupporting,
						::efficientOnly,
						::suitableToolsOnly
					) { defaultValue(false) }
					hide(
						::rotateForBreak,
						::doubleBreak,
						::breaksPerTick,
						::sorter,
						::unsafeCancels,
						::tickStageMask,
						::maxPendingBreaks
					)
				}
				hide(hotbarConfig::tickStageMask)
				hideAllGroupsExcept(breakConfig, rotationConfig, hotbarConfig)
			}
        }

        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()

	        val breakContexts =
				runSafeAutomated {
					buildMap { put(event.pos, TargetState.Empty) }
						.simulate()
						.filterIsInstance<BreakResult.Break>()
						.map { it.context }
						.takeIf { it.isNotEmpty() }
				} ?: return@listen

            breakRequest(breakContexts, pendingInteractions).submit()
        }
    }
}
