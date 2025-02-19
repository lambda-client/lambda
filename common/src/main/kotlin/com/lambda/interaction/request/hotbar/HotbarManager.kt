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

package com.lambda.interaction.request.hotbar

import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.RequestHandler
import com.lambda.threading.runSafe
import com.lambda.mixin.entity.PlayerInventoryMixin
import com.lambda.mixin.render.InGameHudMixin

/**
 * See mixins:
 * @see PlayerInventoryMixin.handleSpoofedMainHandStack
 * @see PlayerInventoryMixin.handleSpoofedBlockBreakingSpeed
 * @see InGameHudMixin.onTick
 */
object HotbarManager : RequestHandler<HotbarRequest>(), Loadable {
    val serverSlot get() = runSafe {
        interaction.lastSelectedSlot
    } ?: 0

    override fun load() = "Loaded Hotbar Manager"

    init {
        listen<InventoryEvent.HotbarSlot.Update> {
            it.slot = currentRequest?.slot ?: return@listen
        }

        listen<TickEvent.Pre> {
            updateRequest()
        }

        listen<TickEvent.Post> {
            val request = currentRequest ?: return@listen

            request.keepTicks--
            request.switchPause--

            if (request.keepTicks <= 0) {
                currentRequest = null
            }
        }
    }
}