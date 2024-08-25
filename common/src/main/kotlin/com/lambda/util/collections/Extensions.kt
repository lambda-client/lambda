package com.lambda.util.collections

import kotlin.reflect.KClass

/**
 * Filters elements of the iterable by their runtime type and a predicate, and adds the matching elements to the specified mutable collection.
 *
 * This function allows filtering elements of an iterable based on their runtime type and a provided predicate function.
 * The elements that match both the type constraint and the predicate are added to the destination mutable collection.
 * Because we do not want additional overhead, this function acts as pointer receiver to a collection.
 * The predicate function determines whether an element should be included based on its type and any additional criteria.
 *
 * @param R The target type to filter elements to.
 * @param C The type of the destination mutable collection.
 * @param destination The mutable collection to which the filtered elements will be added.
 * @param iterator The iterator function that processes the filtered elements and their index.
 * @param predicate The predicate function that determines whether an element should be included based on its type and other criteria.
 */
inline fun <reified R, C : MutableCollection<in R>> Iterable<*>.filterPointer(
    destination: C?,
    iterator: (R, Int) -> Unit,
    predicate: (R) -> Boolean,
) {
    var index = 0

    forEach { element ->
        val fulfilled = predicate(element as R)

        if (fulfilled && destination != null) {
            destination.add(element)
            iterator(element, index)
        }

        index++
    }
}

/**
 * Filters elements of the iterable by their runtime type and a predicate, and adds the matching elements to the specified mutable collection.
 *
 * This function allows filtering elements of an iterable based on their runtime type and a provided predicate function.
 * The elements that match both the type constraint and the predicate are added to the destination mutable collection.
 * Because we do not want additional overhead, this function acts as pointer receiver to a collection.
 * The predicate function determines whether an element should be included based on its type and any additional criteria.
 *
 * @param R The target type to filter elements to.
 * @param C The type of the destination mutable collection.
 * @param destination The mutable collection to which the filtered elements will be added.
 * @param iterator The iterator function that processes the filtered elements and their index.
 * @param predicate The predicate function that determines whether an element should be included based on its type and other criteria.
 */
inline fun <R : Any, C : MutableCollection<in R>> Iterable<*>.filterPointer(
    kClass: KClass<out R>,
    destination: C?,
    iterator: (R) -> Unit,
    predicate: (R) -> Boolean,
) {
    forEach { element ->
        // Cannot be replaced with reified type due to type erasure
        (element as? R) ?: return@forEach
        val fulfilled = kClass.isInstance(element) && predicate(element)

        if (fulfilled && destination != null) {
            destination.add(element)
            iterator(element)
        }
    }
}
