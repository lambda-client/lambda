
package com.minato.config

import kotlin.reflect.KProperty

class ConfigBlockWrapper<T : ConfigBlock>(
	val configBlock: T,
	val layer: ConfigBlockLayer
) {
	operator fun getValue(thisRef: Any?, property: KProperty<*>) = configBlock
	operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {}
}