
package com.minato.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Pointer<T>(var value: T? = null) : ReadWriteProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        this.value = value
    }
}
