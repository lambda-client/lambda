
package com.minato.event.events

import com.minato.event.Event
import com.minato.module.Module

/**
 * Represents events related to toggling, enabling, and disabling of [Module]s.
 *
 * @see Toggle
 * @see Enabled
 * @see Disabled
 */
sealed class ModuleEvent {
	/**
	 * Event that fires before a [Module] is toggled.
	 */
	data class Toggle(val module: Module, val newValue: Boolean) : Event

	/**
	 * Event that fires before a [Module] is enabled.
	 */
	data class Enabled(val module: Module) : Event

	/**
	 * Event that fires before a [Module] is disabled.
	 */
	data class Disabled(val module: Module) : Event
}