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

package com.lambda.util

import com.lambda.util.DynamicReflectionSerializer.simpleRemappedName
import kotlin.collections.toTypedArray

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