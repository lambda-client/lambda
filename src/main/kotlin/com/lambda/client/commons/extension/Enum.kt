package com.lambda.client.commons.extension

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.util.text.capitalize

fun <E : Enum<E>> E.next(): E = declaringClass.enumConstants.run {
    get((ordinal + 1) % size)
}

fun Enum<*>.readableName() = (this as? DisplayEnum)?.displayName
    ?: name.mapEach('_') { low -> low.lowercase().capitalize() }.joinToString(" ")