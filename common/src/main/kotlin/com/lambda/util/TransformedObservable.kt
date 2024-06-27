package com.lambda.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class TransformedObservable<T>(initialValue: T) : ReadWriteProperty<Any?, T> {
    private var value = initialValue

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val oldValue = this.value
        this.value = transform(value)
        if (oldValue != this.value) onChange(oldValue, this.value)
    }

    protected open fun transform(value: T): T = value
    protected open fun onChange(oldValue: T, newValue: T) {}

    override fun toString(): String = "TransformedObservableProperty(value=$value)"
}
