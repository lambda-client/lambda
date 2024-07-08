package com.lambda.core.registry

import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier


object RegistryController {
    private val REGISTRIES = HashMap<RegistryKey<out Registry<*>?>, MutableList<RegistryHolder<*>>>()

    fun <T> register(registry: Registry<T>, id: Identifier, value: T): RegistryHolder<T> {
        val holder = RegistryHolder(id, value)

        REGISTRIES.computeIfAbsent(registry.key) { mutableListOf(holder) }
        return holder
    }

    /**
     * Loads the temporary registry holders into the actual registry.
     * This should be called after all registry holders have been created.
     */
    fun register(resourceKey: RegistryKey<out Registry<*>?>, registry: RegistryWrapper<*>) {
        REGISTRIES[resourceKey]?.forEach { it.handleRegister(registry) }
        REGISTRIES.remove(resourceKey)
    }
}
