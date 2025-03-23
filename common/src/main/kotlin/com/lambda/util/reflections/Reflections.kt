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

package com.lambda.util.reflections

import com.lambda.util.extension.isObject
import com.lambda.util.extension.objectInstance
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Modifier
import java.util.Objects

val cache = mutableMapOf<Int, Reflections>()

/**
 * This function retrieves or create a reflection instance to avoid redundant
 * reflection calls
 *
 * Every time you instantiate a [Reflections] class, it scans the entire classloader and caches its result in a store
 */
inline fun reflectionCache(block: ConfigurationBuilder.() -> Unit): Reflections {
    val config = ConfigurationBuilder().apply(block)
    val cacheKey = Objects.hash(config.classLoaders, config.urls, config.scanners, config.inputsFilter)

    return cache.getOrPut(cacheKey) { Reflections(config) }
}

/**
 * Retrieves all instances of the specified type `T`.
 *
 * @param T The type of instances to retrieve.
 * @param block A configuration lambda to customize the [ConfigurationBuilder] used to configure Reflections.
 *
 * @return A list of instances of type `T`
 */
inline fun <reified T : Any> getInstances(block: ConfigurationBuilder.() -> Unit = { forPackage("com.lambda") }) =
    reflectionCache(block).getSubTypesOf(T::class.java)
        .mapNotNull { createInstance<T>(it) }

/**
 * Retrieves all resource paths that match the given pattern.
 *
 * The function caches the results based on the configuration provided via the [block] lambda to avoid redundant
 * reflection calls.
 *
 * @param pattern The resource pattern to search for.
 * @param block A configuration lambda to customize the [ConfigurationBuilder] used to configure Reflections.
 *
 * @return A set of resource paths that match the specified pattern.
 */
inline fun getResources(pattern: String, block: ConfigurationBuilder.() -> Unit = { forPackage("com.lambda") }) =
    reflectionCache(block).getResources(pattern)

inline fun <reified T : Any> createInstance(clazz: Class<*>): T? {
    return when {
        clazz.isInterface || clazz.isEnum || clazz.isAnnotation || clazz.isObject -> {
            // Handle objects (singletons) or invalid types
            clazz.objectInstance as? T
        }
        else -> {
            // Look for a constructor with no parameters
            clazz.constructors
                .filterNot { Modifier.isAbstract(it.declaringClass.modifiers) } // Avoid abstract constructors
                .firstOrNull { it.parameterCount == 0 }?.newInstance() as? T
        }
    }
}
