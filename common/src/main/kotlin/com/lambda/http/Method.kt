package com.lambda.http

/**
 * Enum representing HTTP methods.
 *
 * @property value The string representation of the HTTP method.
 */
enum class Method(val value: String) {
    GET("GET"),
    HEAD("HEAD"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE"),
    PATCH("PATCH")
}
