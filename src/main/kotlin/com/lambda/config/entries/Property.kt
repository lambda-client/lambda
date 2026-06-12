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

package com.lambda.config.entries

import com.lambda.config.Config
import com.lambda.config.ConfigEntry
import com.lambda.config.EntryCore
import com.lambda.config.EntryLayer

class Property<T>(
	override val name: String,
	defaultValue: T,
	value: T,
	override val layer: PropertyEntryLayer<T>,
	override val config: Config,
	var setter: (T) -> T,
	var equals: T.(T) -> Boolean
) : ConfigEntry<T> {
	override var core = EntryCore(defaultValue, setter(value))
	override val originalCore = core
	override val isModified get() = !equals.invoke(core.value, core.defaultValue)

	operator fun setValue(thisRef: Any?, property: Any?, newValue: T) {
		core.value = setter(newValue)
	}

	fun reset() {
		setValue(this, null, core.defaultValue)
	}
}

class PropertyEntryLayer<T>(
	override val parent: EntryLayer.Multiple<Property<*>>,
	entrySupplier: (layer: PropertyEntryLayer<T>) -> Property<T>
) : EntryLayer.Single<Property<*>>() {
	override val entry = entrySupplier(this)
}