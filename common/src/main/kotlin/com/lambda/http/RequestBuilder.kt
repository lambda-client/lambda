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
inline fun request(url: String, block: (@RequestDsl RequestBuilder).() -> Unit) =
    RequestBuilder(url).apply(block).build()
