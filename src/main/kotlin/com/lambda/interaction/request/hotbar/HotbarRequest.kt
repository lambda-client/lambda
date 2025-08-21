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

import com.lambda.interaction.request.Request

class HotbarRequest(
    val slot: Int,
    override val config: HotbarConfig,
    override var keepTicks: Int = config.keepTicks,
    override var swapPause: Int = config.swapPause
) : Request(), HotbarConfig by config {
    override val requestID = ++requestCount

    var activeRequestAge = 0
    var swapPauseAge = 0

    val swapPaused get() = swapPauseAge < swapPause
    val swappedThisTick get() = activeRequestAge <= 0
    val keeping get() = keepTicks > 0

    override val done: Boolean
        get() = slot == HotbarManager.serverSlot && !swapPaused

    override fun submit(queueIfClosed: Boolean) =
        HotbarManager.request(this, queueIfClosed)

    companion object {
        var requestCount = 0
    }
}
