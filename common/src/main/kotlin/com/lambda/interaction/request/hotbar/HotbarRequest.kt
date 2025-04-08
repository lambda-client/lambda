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

import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.Request

class HotbarRequest(
    val hotbarConfig: HotbarConfig,
    priority: Priority = 0,
    val actionSequence: HotbarManager.HotbarActionSequence.() -> Unit,
) : Request(priority) {
    var swapSlot: HotbarManager.SlotInfo? = null

    var instantActionsComplete = false
    override val done: Boolean
        get() = swapSlot?.let { it.slot == HotbarManager.serverSlot && !it.swapPaused } ?: true

    constructor (
        slot: Int,
        hotbarConfig: HotbarConfig,
        priority: Priority = 0
    ) : this(
        hotbarConfig,
        priority,
        { if (swapTo(slot, hotbarConfig.keepTicks.coerceAtLeast(1))) done() }
    )
}