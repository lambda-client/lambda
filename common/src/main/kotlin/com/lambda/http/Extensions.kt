package com.lambda.http

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
