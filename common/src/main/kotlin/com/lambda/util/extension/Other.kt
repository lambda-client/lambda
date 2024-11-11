/*
 * Copyright 2024 Lambda
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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Executes the given block only if the object receiver is null
 * Opposite of `Any?.let {}`
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> T?.ifNull(block: () -> Unit): T? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    if (this == null) block()

    return this
}

val Class<*>.isObject: Boolean
    get() = declaredFields.any { it.name == "INSTANCE" }

val Class<*>.objectInstance: Any
    get() = declaredFields.first { it.name == "INSTANCE" }.apply { isAccessible = true }.get(null)
