
package com.minato.core

/**
 * Represents a loadable object.
 */
interface Loadable {
    val priority: Int get() = 0

    fun load() = this::class.simpleName?.let { "Loaded $it" } ?: "Loaded"
}
