
package com.minato.util

import kotlin.properties.ReadOnlyProperty

inline fun <reified T> selfReference(noinline initializer: ReadOnlyProperty<Any?, T>.() -> T) =
    object : ReadOnlyProperty<Any?, T> {
        val value: T by lazy { initializer() }

        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = value
    }
