/*
 * Copyright 2025 Lambda
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

package com.lambda.core

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
    val registries = HashMap<
            RegistryKey<out Registry<*>?>,
            MutableList<Pair<Identifier, *>>
            >()

    /**
     * Registers a temporary registry holder.
     *
     * This should be called before the given registry has been loaded.
     *
     * @param registry The registry to register the holder for.
     * @param id The identifier for the registry entry.
     * @param value The value to register.
     */
    fun <T> register(registry: Registry<T>, id: Identifier, value: T) =
        registries.getOrPut(registry.key) { mutableListOf() }.add(id to value)

    /**
     * Loads the temporary registry holders into the actual registry.
     * This should be called after all registry holders have been created.
     *
     * @param registry The registry to dump into.
     * @param wrapper The registry wrapper to use to determine how to register the entry.
     *
     * @return Whether there were temporary registries or not or null if the registry is null.
     */
    fun <T : Any> dump(registry: Registry<T>, wrapper: RegistryWrapper<T> = defaultWrapper(registry)): Boolean? {
        val key = registry.key

        registries[key]?.forEach { (id, value) ->
            @Suppress("Unchecked_cast")
            val v = value as? T ?: return@forEach
            wrapper.registerForHolder(id, v)
        }

        return registries.remove(key) != null
    }

    /**
     * Default registry wrapper for vanilla registries.
     */
    fun <T : Any> defaultWrapper(registry: Registry<T>) = object : RegistryWrapper<T> {
        override fun registerForHolder(id: Identifier, value: T): RegistryEntry<T> {
            val simple = registry as? SimpleRegistry
            val frozen = simple?.frozen ?: true // will never be null

            simple?.let { it.frozen = false } // fuck off
            val entry = Registry.registerReference(registry, id, value)
            simple?.let { it.frozen = frozen }

            return entry
        }
    }
}
