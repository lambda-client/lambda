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

package com.lambda.util.reflections

import com.lambda.util.extension.isObject
import com.lambda.util.extension.objectInstance
import io.github.classgraph.ClassGraph
import io.github.classgraph.ResourceList
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Modifier
import kotlin.jvm.java

/**
 * Retrieves all instances of the specified type `T`.
 *
 * @param T The type of instances to retrieve.
 * @param block A configuration lambda to customize the [ConfigurationBuilder] used to configure Reflections.
 *
 * @return A list of instances of type `T`
 */
inline fun <reified T : Any> getInstances(block: ClassGraph.() -> Unit = { enableClassInfo(); acceptPackages("com.lambda") }): List<T> =
    ClassGraph().apply(block)
        .scan()
        .use { result ->
            val clazz = T::class.java

            return when {
                clazz.isInterface -> result.getClassesImplementing(T::class.java)

                clazz.isObject || Modifier.isAbstract(clazz.modifiers) ->
                    result.getSubclasses(T::class.java)

                else -> throw IllegalAccessException("class ${clazz.name} is neither an interface or abstract class")
            }.mapNotNull { createInstance<T>(Class.forName(it.name)) }
        }

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
inline fun getResources(pattern: String, block: ClassGraph.() -> Unit = { enableAllInfo(); acceptPackages("com.lambda") }): ResourceList =
    ClassGraph().apply(block)
        .scan()
        .use { it.getResourcesMatchingWildcard(pattern) }

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
