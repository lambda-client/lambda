
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.ConfigEditor.hide
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.event.events.PlayerEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.construction.simulation.context.BuildContext
import com.minato.interaction.managers.breaking.BreakRequest.Companion.breakRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafeAutomated
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("unused")
object FastBreak : Module(
	name = "FastBreak",
	description = "Break blocks faster.",
	tag = ModuleTag.PLAYER,
) {
	private val pendingActions = ConcurrentLinkedQueue<BuildContext>()

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::buildConfig, ::breakConfig, ::rotationConfig, ::hotbarConfig)
				buildConfig.apply {
					hide(
						::pathing,
						::spleefEntities,
						::maxBuildDependencies,
						::collectDrops,
						::blockReach,
						::entityReach,
						::breakBlocks,
						::interactBlocks,
						::placeBlocks
					)
					::maxBuildDependencies.editSetting { defaultValue(0) }
					editTypedSettings(
						::strictRayCast
					) { defaultValue(false); }
					hide(::strictRayCast, ::checkSideVisibility)
					::blockReach.editSetting { defaultValue(Double.MAX_VALUE) }
				}
				breakConfig.apply {
					editTypedSettings(
						::avoidFluids,
						::avoidSupporting,
						::efficientOnly,
						::suitableToolsOnly
					) { defaultValue(false) }
					editTypedSettings(
						::rotate,
						::doubleBreak
					) { defaultValue(false); hide() }
					::breaksPerTick.editSetting { defaultValue(1); hide() }
					::tickStageMask.editSetting { defaultValue(mutableSetOf(TickEvent.Input.Post)); hide() }
					hide(::sorter, ::unsafeCancels)
				}
				hotbarConfig::tickStageMask.editSetting { defaultValue(mutableSetOf(TickEvent.Input.Post)); hide() }
			}

		listen<PlayerEvent.Attack.Block> { it.cancel() }
		listen<PlayerEvent.Breaking.Update> { event ->
			event.cancel()
			runSafeAutomated {
				breakRequest(listOf(event.pos), pendingActions)?.submit()
			}
		}
	}
}
