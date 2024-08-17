package com.lambda.util

class SelfReference<T>(initializer: SelfReference<T>.() -> T)  {
    val self: T by lazy { inner }

    private val inner = initializer()
    operator fun getValue(thisRef: Any?, property: Any?) = self
}

fun <T> selfReference(initializer: SelfReference<T>.() -> T): SelfReference<T> = SelfReference(initializer)
