/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.core.registry

import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.SimpleRegistry
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
     * @return Whether there were temporary registries or not or null if the registry is null.
     */
    fun dump(registry: Registry<*>?, wrapper: RegistryWrapper<*>?): Boolean? {
        if (registry == null || registry !is SimpleRegistry) return null
        val key = registry.key

        registries[key]?.forEach { it.handleRegister(wrapper ?: defaultWrapper(registry)) }
        return registries.remove(key) != null
    }

    /**
     * Default registry wrapper for vanilla registries.
     */
    @Suppress("UNCHECKED_CAST")
    private fun defaultWrapper(registry: SimpleRegistry<*>): RegistryWrapper<*> {
        return object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                registry.frozen = false // fuck off
                val entry = Registry.registerReference(registry as Registry<T>, id, value)
                registry.frozen = true

                return entry
            }
        }
    }
}
