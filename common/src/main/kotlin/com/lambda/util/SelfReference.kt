package com.lambda.util

class SelfReference<T>(initializer: SelfReference<T>.() -> T)  {
    val self: T by lazy { inner ?: throw IllegalStateException("Do not use `self` until initialized.") }

    private val inner = initializer()
    operator fun getValue(thisRef: Any?, property: Any?) = self
}

fun <T> selfReference(initializer: SelfReference<T>.() -> T): SelfReference<T> = SelfReference(initializer)
