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
import java.io.PrintStream
import java.io.PrintWriter

class DynamicException(original: Throwable) : Throwable(original) {
    private fun Array<StackTraceElement>.remapClassNames() =
        map { element ->
            StackTraceElement(
                element.className.simpleRemappedName,
                element.methodName.simpleRemappedName,
                element.fileName,
                element.lineNumber
            )
        }.toTypedArray()

    override fun printStackTrace(s: PrintStream) =
        stackTrace.forEach { s.println("\tat $it") }

    override fun printStackTrace(s: PrintWriter) =
        stackTrace.forEach { s.println("\tat $it") }

    override fun toString(): String = localizedMessage

    init {
        stackTrace = stackTrace.remapClassNames()
    }
}
