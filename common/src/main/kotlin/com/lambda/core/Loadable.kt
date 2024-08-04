package com.lambda.core

/**
 * Represents a loadable object.
 */
interface Loadable {
    fun load() = this::class.simpleName?.let { "Loaded $it" } ?: "Loaded"
}
