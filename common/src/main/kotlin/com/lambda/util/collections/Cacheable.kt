package com.lambda.util.collections

import kotlin.reflect.KProperty

class Cacheable<K, V> private constructor(private val getter: (K) -> V) {
    private val cache = mutableMapOf<K, V>()

    operator fun getValue(thisRef: K, property: KProperty<*>) =
        cache.getOrPut(thisRef) { getter(thisRef) }

    companion object {
        fun <K, V> cacheable(getter: (K) -> V) = Cacheable(getter)
    }
}