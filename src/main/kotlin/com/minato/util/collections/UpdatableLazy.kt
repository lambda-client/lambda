
package com.minato.util.collections

/**
 * A lazy-initialized value holder that allows the stored value to be reset and re-initialized on demand.
 */
class UpdatableLazy<T>(private val initializer: () -> T) {
    private var backingField: T? = null

    val value: T
        get() = backingField ?: initializer().also { backingField = it }

    fun update() {
        backingField = initializer()
    }
}

fun <T> updatableLazy(initializer: () -> T) = UpdatableLazy(initializer)