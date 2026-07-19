
package com.minato.config

import kotlin.reflect.KProperty

interface ConfigEntry<T> {
	val name: String
	val originalCore: EntryCore<T>
	var core: EntryCore<T>
	val layer: EntryLayer.Single<*>
	val config: Config

	val isModified: Boolean

	operator fun getValue(thisRef: Any?, property: Any?) = core.value
	operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) { core.value = newValue }
}

class EntryCore<T>(
	var defaultValue: T,
	var value: T = defaultValue,
)