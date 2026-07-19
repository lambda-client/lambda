
package com.minato.module.modules.debug

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.event.events.PlayerEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.hotbar.HotbarRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info

object SilentSwap : Module(
    name = "SilentSwap",
    description = "SilentSwap",
    tag = ModuleTag.DEBUG,
) {
    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::hotbarConfig)
            }

        listen<PlayerEvent.Attack.Block> {
            if (!HotbarRequest(0, this@SilentSwap).submit().done) {
                it.cancel()
                return@listen
            }
            info("${interaction.lastSelectedSlot} ${player.mainHandStack}")
        }
    }
}
