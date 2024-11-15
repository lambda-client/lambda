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

package com.lambda.http

import com.lambda.Lambda
import java.net.URLEncoder

/**
 * Extension property to convert a map to a URL query string.
 */
val Map<String, Any>.query: String
    get() = map { (key, value) -> "$key=${value.urlEncoded}" }.joinToString("&")

/**
 * Extension property to URL encode a string.
 */
val Any.urlEncoded: String get() = URLEncoder.encode(toString(), "UTF-8")

/**
 * Extension function to convert a map to a JSON string.
 */
fun Map<String, Any>.toJson(): String = Lambda.gson.toJson(this)

/**
 * Try-catch block wrapped with a default value.
 */
fun <T> tryOrDefault(default: T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    default
}
