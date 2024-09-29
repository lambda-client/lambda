package com.lambda.core.registry

import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier

/**
 * Controller for handling temporary registry holders.
 * This is used to register entries into registries before they are actually loaded.
 * It allows for environment-agnostic registry handling.
 */
object AgnosticRegistries {
    private val registries = HashMap<
            RegistryKey<out Registry<*>?>,
            MutableList<RegistryHolder<*>>
            >()

    /**
     * Registers a temporary registry holder.
     * This should be called after the controller has dumped its registries.
     * If not, the holder will not be dumped into its respective registry.
     *
     * @param registry The registry to register the holder for.
     * @param id The identifier for the registry entry.
     * @param value The value to register.
     *
     * @return The registry holder.
     */
    fun <T> register(registry: Registry<T>, id: Identifier, value: T): RegistryHolder<T> {
        val holder = RegistryHolder(id, value)

        registries.getOrPut(registry.key) { mutableListOf() }.add(holder)
        return holder
    }

    /**
     * Loads the temporary registry holders into the actual registry.
     * This should be called after all registry holders have been created.
     *
     * @param registry The registry to dump into.
     */
    fun dump(registry: Registry<*>?) = dump(registry, wrapper = null)

    /**
     * Loads the temporary registry holders into the actual registry.
     * This should be called after all registry holders have been created.
     *
     * @param registry The registry to dump into.
     * @param wrapper The registry wrapper to use to determine how to register the entry.
     *
     * @return Whether there were temporary registries or not.
     */
    fun dump(registry: Registry<*>?, wrapper: RegistryWrapper<*>?): Boolean {
        val key = registry?.key

        registries[key]?.forEach { it.handleRegister(wrapper ?: defaultWrapper(registry)) }
        return registries.remove(key) != null
    }

    /**
     * Default registry wrapper for vanilla registries.
     */
    private fun defaultWrapper(registry: Registry<*>?): RegistryWrapper<*> {
        return object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                @Suppress("UNCHECKED_CAST")
                return Registry.registerReference(registry as Registry<T>, id, value)
            }
        }
    }
}
