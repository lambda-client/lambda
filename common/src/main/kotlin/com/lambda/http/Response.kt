package com.lambda.http

import java.io.BufferedReader
import java.net.HttpURLConnection

/**
 * Represents an HTTP response.
 */
class Response(
    /**
     * The HTTP connection associated with the response.
     */
    var connection: HttpURLConnection? = null,

    /**
     * The buffered reader for reading the response body.
     */
    var body: BufferedReader? = null,

    /**
     * The exception that occurred during the request, if any.
     */
    var exception: Throwable? = null,
) {
    /**
     * Indicates whether the request was successful (HTTP status code 2xx).
     */
    val success: Boolean
        get() = connection?.let { return it.responseCode in 200..299 } ?: false
}
