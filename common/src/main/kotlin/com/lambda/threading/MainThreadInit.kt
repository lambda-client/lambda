package com.lambda.threading

import kotlin.reflect.KProperty

class MainThreadInit<T : Any>(private val initializer: () -> T) {
    private lateinit var value: T

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value

    init {
        runGameScheduled {
            value = initializer()
        }
    }
}

fun <T : Any> mainThread(initializer: () -> T) = MainThreadInit(initializer)