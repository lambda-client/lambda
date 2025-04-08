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
import com.lambda.interaction.request.RequestConfig

/*
 * Abstract base class for configuring hotbar slot switch behavior.
 *
 * @param priority The priority of this configuration.
 */
abstract class HotbarConfig(
    priority: Priority
) : RequestConfig<HotbarRequest>(priority) {

    /**
     * The number of ticks to keep the current hotbar selection active.
     */
    abstract val keepTicks: Int

    /**
     * The delay, in ticks, between swapping hotbar selections
     */
    abstract val swapDelay: Int

    /**
     * The amount of hotbar selection swaps that can happen per tick
     *
     * Only makes a difference if swapDelay is set to 0
     */
    abstract val swapsPerTick: Int

    /**
     * The delay in ticks to pause actions after switching to the slot.
     *
     * Affects the validity state of the request
     */
    abstract var swapPause: Int

    /**
     * Registers a hotbar request with the HotbarManager.
     *
     * @param request The hotbar request to register.
     */
    override fun requestInternal(request: HotbarRequest) {
        HotbarManager.registerRequest(this, request)
    }
}