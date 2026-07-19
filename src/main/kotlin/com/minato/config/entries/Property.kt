
package com.minato.config.entries

import com.minato.config.Config
import com.minato.config.ConfigEntry
import com.minato.config.EntryCore
import com.minato.config.EntryLayer

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