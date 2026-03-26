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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.managers.hotbar.HotbarConfig
import com.lambda.util.NamedEnum

class HotbarSettings(
    c: Configurable,
    vararg baseGroup: NamedEnum,
    prefix: String = "",
    override val visibility: () -> Boolean = { true },
) : SettingGroup(c), HotbarConfig {
    override val swapMode by c.setting("${prefix}Swap Mode", HotbarConfig.SwapMode.Temporary, visibility = visibility).group(*baseGroup).index()
    override val keepTicks by c.setting("${prefix}Keep Ticks", 1, 0..20, 1, "The number of ticks to keep the current hotbar selection active", " ticks") { visibility() && swapMode == HotbarConfig.SwapMode.Temporary }.group(*baseGroup).index()
    override val swapDelay by c.setting("${prefix}Swap Delay", 0, 0..3, 1, "The number of ticks delay before allowing another hotbar selection swap", " ticks", visibility = visibility).group(*baseGroup).index()
    override val swapsPerTick by c.setting("${prefix}Swaps Per Tick", 3, 1..10, 1, "The number of hotbar selection swaps that can take place each tick") { visibility() && swapDelay <= 0 }.group(*baseGroup).index()
    override val swapPause by c.setting("${prefix}Swap Pause", 0, 0..20, 1, "The delay in ticks to pause actions after switching to the slot", " ticks", visibility = visibility).group(*baseGroup).index()
    override val tickStageMask by c.setting("${prefix}Hotbar Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which hotbar actions are performed", displayClassName = true, visibility = visibility).group(*baseGroup).index()
}