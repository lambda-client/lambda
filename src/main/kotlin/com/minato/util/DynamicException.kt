
package com.minato.util

import com.minato.util.DynamicReflectionSerializer.simpleRemappedName

/**
 * Remaps the stacktrace in production to have readable, class, method and field names
 */
fun dynamicException(original: Throwable) = Throwable(original.localizedMessage)
    .apply {
        stackTrace = stackTrace.map { element ->
            StackTraceElement(
                element.className.simpleRemappedName,
                element.methodName.simpleRemappedName,
                element.fileName, // This is intentional, you don't need to remap the file name so might as well keep a reference of the class file name
                element.lineNumber
            )
        }.toTypedArray()
    }