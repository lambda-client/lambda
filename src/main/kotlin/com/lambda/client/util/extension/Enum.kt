package com.lambda.client.util.extension

import com.lambda.client.util.interfaces.DisplayEnum
import com.lambda.client.util.text.capitalize

fun <E : Enum<E>> E.next(): E = declaringJavaClass.enumConstants.run {
    get((ordinal + 1) % size)
}

fun <E : Enum<E>> E.previous(): E = declaringJavaClass.enumConstants.run {
    get((ordinal - 1).mod(size))
}

fun Enum<*>.readableName() = (this as? DisplayEnum)?.displayName
    ?: name.mapEach('_') { low -> low.lowercase().capitalize() }.joinToString(" ")