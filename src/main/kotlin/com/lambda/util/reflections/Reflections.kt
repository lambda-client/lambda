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
import io.github.classgraph.ClassInfo
import io.github.classgraph.Resource
import io.github.classgraph.ResourceList
import io.github.classgraph.ScanResult
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

val scanResult: ScanResult by lazy { ClassGraph().enableAllInfo().scan() }

val Any.className: String get() = this::class.java.name
    .substringAfter("${this::class.java.packageName}.")
    .replace('$', '.')

val KClass<*>.className: String get() = java.name
    .substringAfter("${java.packageName}.")
    .replace('$', '.')


/**
 * This function returns a instance of subtype [T].
 *
 * When [T] is an interface, the function with returns a list of classes that implements [T].
 * When [T] is an abstract class (open classes too), the function will return a list of classes extending [T].
 *
 * Only final classes with empty constructors will be created.
 */
inline fun <reified T : Any> getInstances(crossinline block: (ClassInfo) -> Boolean = { true }): List<T> {
    if (scanResult.isClosed) return emptyList()

    val clazz = T::class.java

    return when {
        clazz.isInterface -> scanResult.getClassesImplementing(clazz)
        Modifier.isAbstract(clazz.modifiers) -> scanResult.getSubclasses(clazz)

        else -> throw IllegalStateException("class ${clazz.name} is neither an interface or open class")
    }.filter { block(it) }
        .mapNotNull { createInstance<T>(Class.forName(it.name)) }
}

@JvmName("getInstancesImplementingWithParameters1")
inline fun <reified T, reified A1> getInstancesImplementingWithParameters() = getInstancesImplementingWithParameters<T>(A1::class)

@JvmName("getInstancesImplementingWithParameters2")
inline fun <reified T, reified A1, reified A2> getInstancesImplementingWithParameters() = getInstancesImplementingWithParameters<T>(A1::class, A2::class)

@JvmName("getInstancesImplementingWithParameters3")
inline fun <reified T, reified A1, reified A2, reified A3> getInstancesImplementingWithParameters() = getInstancesImplementingWithParameters<T>(A1::class, A2::class, A3::class)

inline fun <reified T> getInstancesImplementingWithParameters(vararg arguments: KClass<*>) =
    scanResult.getClassesImplementing(T::class.java)
        .filter {
            it.typeSignature.superinterfaceSignatures
                .any { int -> int.typeArguments
                    .any { arg -> arguments.any { it.qualifiedName == arg.typeSignature.toString() }} }
        }

inline fun getResources(pattern: String, crossinline block: (Resource) -> Boolean): ResourceList =
    scanResult.getResourcesMatchingWildcard(pattern)
        .filter { block(it) }

inline fun <reified T> createInstance(clazz: Class<*>): T? {
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
