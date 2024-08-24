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

/**
 * Try-catch block wrapped with null
 */
fun <T> tryOrNull(block: () -> T): T? = tryOrDefault(null, block)
