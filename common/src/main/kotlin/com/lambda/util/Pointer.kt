package com.lambda.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A class representing a pointer to a value.
 *
 * It is a high-level abstraction over a mutable variable that allows for easy access to the value.
 */
data class Pointer<T>(var value: T? = null) : ReadWriteProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        this.value = value
    }
}
