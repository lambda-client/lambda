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

package com.lambda.module.modules.debug

import com.lambda.config.ConfigEditor.hideAllExcept
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.withEdits
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.CommunicationUtils.info

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
            if (!hotbarRequest(0).submit().done) {
                it.cancel()
                return@listen
            }
            info("${interaction.lastSelectedSlot} ${player.mainHandStack}")
        }
    }
}
