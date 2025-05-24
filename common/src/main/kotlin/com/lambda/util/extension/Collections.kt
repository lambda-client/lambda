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

package com.lambda.util.extension

import kotlin.reflect.KClass

/**
 * Filters elements by their type and adds the result in the destination
 *
 * @param R             The target type to filter elements to
 * @param C             The type of the destination mutable collection
 * @param destination   The mutable collection to which the filtered elements will be added
 * @param predicate     The predicate function that determines whether an element should be included based on its type and other criteria
 */
inline fun <R : Any, C : MutableCollection<in R>> Iterable<*>.filterPointer(
    kClass: KClass<out R>,
    destination: C?,
    predicate: (R) -> Boolean,
) =
    @Suppress("UNCHECKED_CAST")
    forEach { element ->
        // Cannot be replaced with reified type due to type erasure
        (element as? R) ?: return@forEach
        val fulfilled = kClass.isInstance(element) && predicate(element)

        if (fulfilled && destination != null)
            destination.add(element)
    }
