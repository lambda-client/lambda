package com.lambda.core.registry

import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import java.util.function.Supplier


class RegistryHolder<T> internal constructor(
    val id: Identifier,
    var value: T,
) : Supplier<T> {
    private var holder: RegistryEntry<T>? = null

    override fun get(): T {
        with(holder) {
            checkNotNull(this) { "RegistryHolder not populated" }

            return value()
        }
    }

    fun handleRegister(registry: RegistryWrapper<*>) {
        holder = registry.registerForHolder(id, value)
    }
}
