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

import com.lambda.Lambda.LOG
import com.lambda.util.extension.isObject
import com.lambda.util.extension.objectInstance
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * Retrieves instances of objects of the specified type using a DSL configuration.
 *
 * @param T The type of objects to retrieve instances for.
 * @param block A DSL block used to configure the [ReflectionConfigDsl].
 * @return A [List] of object instances of type [T], or null if no instances can be found.
 */
inline fun <reified T : Any> getInstances(block: ReflectionConfigDsl.() -> Unit): List<T> =
    getInstances<T>(getConfiguration(block))

/**
 * Retrieves instances of objects of the specified type using a provided configuration.
 *
 * @param T The type of objects to retrieve instances for.
 * @param config A pre-configured [ConfigurationBuilder].
 * @return A [List] of object instances of type [T], or null if no instances can be found.
 */
inline fun <reified T : Any> getInstances(config: ConfigurationBuilder): List<T> =
    getSubTypesOf<T>(config).mapNotNull { clazz: Class<*> ->
        if (!clazz.isInterface
            && !clazz.isEnum
            && !clazz.isAnnotation
            && !clazz.isObject
        ) clazz.constructors.filter { !Modifier.isAbstract(clazz.modifiers) }.firstOrNull { it.parameterCount == 0 }
            ?.newInstance() as? T
            ?: null.also { LOG.debug("Could not find a proper no-arg constructor for the class ${clazz.simpleName}.") }
        else clazz.objectInstance as? T
        // We're doomed at this point, now I am become death, the destroyer of worlds
            ?: null.also {
                LOG.debug(
                    "No instance of type ${T::class.java.simpleName} could be found on the class ${clazz.simpleName}." +
                            " Ensure that the class has an INSTANCE field or a no-arg constructor."
                )
            }
    }

/**
 * Finds all subtypes of a specified class
 *
 * @param T The type for which to find subtypes.
 * @param config A pre-configured [ConfigurationBuilder].
 * @return A [Set] of [Class] objects representing all subtypes of the specified type.
 */
inline fun <reified T : Any> getSubTypesOf(config: ConfigurationBuilder): Set<Class<*>> =
    Reflections(config).getSubTypesOf(T::class.java)


/**
 * Retrieves resources matching a specified pattern
 *
 * @param pattern The pattern to match resources against.
 * @param config A pre-configured [ConfigurationBuilder].
 * @return A [Set] of [String] representing resources matching the specified pattern.
 */
fun getResources(pattern: String, config: ConfigurationBuilder): Set<String> =
    Reflections(config).getResources(pattern)

/**
 * Retrieves resources matching a specified [Pattern]
 *
 * @param pattern The [Pattern] to match resources against.
 * @param block A DSL block used to configure the [ReflectionConfigDsl].
 * @return A [Set] of [String] representing resources matching the specified pattern.
 */
inline fun getResources(pattern: Pattern, block: ReflectionConfigDsl.() -> Unit): Set<String> =
    getResources(pattern.pattern(), config = getConfiguration(block))
