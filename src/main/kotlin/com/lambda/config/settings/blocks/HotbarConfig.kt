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

package com.lambda.config.settings.blocks

import com.lambda.event.events.TickEvent

/**
 * Abstract base class for configuring hotbar slot switch behavior.
 *
 * @param priority The priority of this configuration.
 */
interface HotbarConfig {
	val swapMode: SwapMode
	/**
	 * The number of ticks to keep the current hotbar selection active.
	 */
	val keepTicks: Int

	/**
	 * The delay, in ticks, between swapping hotbar selections
	 */
	val swapDelay: Int

	/**
	 * The amount of hotbar selection swaps that can happen per tick
	 *
	 * Only makes a difference if swapDelay is set to 0
	 */
	val swapsPerTick: Int

	/**
	 * The delay in ticks to pause actions after switching to the slot.
	 *
	 * Affects the validity state of the request
	 */
	val swapPause: Int

	/**
	 * The sub-tick timings at which hotbar actions can be performed
	 */
	val tickStageMask: Collection<TickEvent>

	enum class SwapMode {
		Temporary,
		Permanent
	}
}