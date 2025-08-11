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
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.util.NamedEnum

class HotbarSettings(
    c: Configurable,
    baseGroup: NamedEnum,
    priority: Priority = 0,
    vis: () -> Boolean = { true }
) : HotbarConfig(priority) {
    override val keepTicks by c.setting("Keep Ticks", 3, 0..20, 1, "The number of ticks to keep the current hotbar selection active", " ticks", vis).group(baseGroup)
    override var switchPause by c.setting("Switch Pause", 0, 0..20, 1, "The delay in ticks to pause actions after switching to the slot", " ticks", vis).group(baseGroup)
}