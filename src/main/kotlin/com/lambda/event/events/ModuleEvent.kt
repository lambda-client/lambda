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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.module.Module

/**
 * Represents events related to toggling, enabling, and disabling of [Module]s.
 *
 * @see Toggle
 * @see Enabled
 * @see Disabled
 */
sealed class ModuleEvent {
	/**
	 * Event that fires before a [Module] is toggled, allowing listeners to cancel the toggle.
	 */
	data class Toggle(val module: Module, val newValue: Boolean) : Event, ICancellable by Cancellable()

	/**
	 * Event that fires before a [Module] is enabled, allowing listeners to cancel the enable.
	 */
	data class Enabled(val module: Module) : Event, ICancellable by Cancellable()

	/**
	 * Event that fires before a [Module] is disabled, allowing listeners to cancel the disable.
	 */
	data class Disabled(val module: Module) : Event, ICancellable by Cancellable()
}