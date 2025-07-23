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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.event.events.TickEvent
import com.lambda.interaction.request.hotbar.HotbarConfig

class HotbarSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : HotbarConfig {
    override val keepTicks by c.setting("Keep Ticks", 3, 0..20, 1, "The number of ticks to keep the current hotbar selection active", " ticks", visibility = vis)
    override val swapDelay by c.setting("Swap Delay", 0, 0..3, 1, "The number of ticks delay before allowing another hotbar selection swap", " ticks", visibility = vis)
    override val swapsPerTick by c.setting("Swaps Per Tick", 3, 1..10, 1, "The number of hotbar selection swaps that can take place each tick") { swapDelay <= 0 && vis() }
    override val swapPause by c.setting("Swap Pause", 0, 0..20, 1, "The delay in ticks to pause actions after switching to the slot", " ticks", visibility = vis)
    override val sequenceStageMask by c.setting("Hotbar Stage Mask", setOf(TickEvent.Input.Pre, TickEvent.Input.Post), "The sub-tick timing at which hotbar actions are performed", visibility = vis)
}
