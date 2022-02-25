package com.lambda.client.commons.utils

import org.reflections.Reflections

object ClassUtils {

    inline fun <reified T> findClasses(
        pack: String,
        noinline block: Sequence<Class<out T>>.() -> Sequence<Class<out T>> = { this }
    ): List<Class<out T>> {
        return findClasses(pack, T::class.java, block)
    }

    fun <T> findClasses(
        pack: String,
        subType: Class<T>,
        block: Sequence<Class<out T>>.() -> Sequence<Class<out T>> = { this }
    ): List<Class<out T>> {
        return Reflections(pack).getSubTypesOf(subType).asSequence()
            .run(block)
            .sortedBy { it.simpleName }
            .toList()
    }

    @Suppress("UNCHECKED_CAST")
    val <T> Class<out T>.instance
        get() = this.getDeclaredField("INSTANCE")[null] as T
}