package com.lambda.http

import java.net.HttpURLConnection

/**
 * Represents an HTTP response.
 */
class Response<Success : Any>(
    /**
     * The response
     */
    var data: Success? = null,

    /**
     * The error
     */
    var error: Throwable? = null,

    /**
     * The HTTP connection associated with the response.
     */
    var connection: HttpURLConnection? = null,
) {
    /**
     * Indicates whether the request was successful (HTTP status code 2xx).
     */
    val success: Boolean
        get() = connection?.let { return it.responseCode in 200..299 } ?: false
}
