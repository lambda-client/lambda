
package com.minato.util.collections

import kotlin.reflect.KProperty

/**
 * This structure lazily evaluates the [getter] lambda and stores its result in its [cache].
 *
 * Example:
 * ```kt
 * val String.sha256: String by cacheable { it.hashString("SHA-256") }
 *
 * val hash1 = "aa".sha256 // lazily evaluated and stored
 * val hash2 = "aa".sha256 // value fetched from the cache
 * val hash3 = "bb.sha256 // lazily evaluated and stored
 * ```
 */
class Cacheable<K, V> private constructor(private val getter: (K) -> V) {
    private val cache = mutableMapOf<K, V>()

    operator fun getValue(thisRef: K, property: KProperty<*>) =
        cache.getOrPut(thisRef) { getter(thisRef) }

    companion object {
        fun <K, V> cacheable(getter: (K) -> V) = Cacheable(getter)
    }
}
