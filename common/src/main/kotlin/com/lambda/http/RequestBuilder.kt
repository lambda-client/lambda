package com.lambda.http

import java.net.HttpURLConnection

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class RequestDsl

@RequestDsl
class RequestBuilder(
    private val url: String,
) {
    private var method: Method = Method.GET
    private val parameters: MutableMap<String, Any> = mutableMapOf()
    private val headers: MutableMap<String, String> = mutableMapOf()
    private var config: ((HttpURLConnection) -> Unit) = {}

    /**
     * Sets the HTTP method to be used for the request.
     */
    fun method(method: Method): RequestBuilder {
        this.method = method
        return this
    }

    /**
     * Sets the query parameters to be included in the request.
     */
    fun parameters(parameters: Map<String, Any>): RequestBuilder {
        this.parameters.putAll(parameters)
        return this
    }

    /**
     * Sets the headers to be included in the request.
     */
    fun headers(headers: Map<String, String>): RequestBuilder {
        this.headers.putAll(headers)
        return this
    }

    /**
     * Sets the lambda function to configure the HTTP connection.
     */
    fun config(config: (HttpURLConnection) -> Unit): RequestBuilder {
        this.config = config
        return this
    }

    fun build() = Request(url, method, parameters, headers, config)
}

/**
 * Creates an HTTP request.
 *
 * @param url The URL to which the request will be made.
 * @param block A lambda function to configure the request.
 */
inline fun request(url: String, block: (@RequestDsl RequestBuilder).() -> Unit) = RequestBuilder(url).apply(block).build()
