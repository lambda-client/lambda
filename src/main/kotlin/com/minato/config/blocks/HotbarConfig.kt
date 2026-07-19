
package com.minato.config.blocks

import com.minato.event.events.TickEvent

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