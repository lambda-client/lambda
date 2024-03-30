package com.lambda.http

import com.lambda.Lambda
import com.lambda.event.EventFlow
import com.lambda.threading.runConcurrent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Represents an HTTP request handler that allows making various HTTP requests.
 *
 * @property url The URL to which the request will be made.
 * @property method The HTTP method to be used for the request. Default is [Method.GET].
 * @property parameters A map of query parameters to be included in the request. Default is an empty map.
 * @property headers A map of headers to be included in the request. Default is an empty map.
 * @property config A lambda function to configure the HTTP connection. Default is an empty lambda.
 */
class Request(
    private val url: String,
    private val method: Method = Method.GET,
    private val parameters: Map<String, Any> = mapOf(),
    private val headers: Map<String, String> = mapOf(),
    private val config: ((HttpURLConnection) -> Unit) = {},
) {

    /**
     * Executes the HTTP request synchronously.
     *
     * @param completion A callback function to handle the response asynchronously.
     */
    fun doRequest(): Response =
        runCatching {
            val url = URL(
                if (parameters.isNotEmpty()) "$url?${parameters.query}"
                else url
            )

            val connection = url.openConnection() as HttpURLConnection
            config.invoke(connection)
            connection.requestMethod = method.value
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

            if (method == Method.POST || method == Method.PUT) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(parameters.query.toByteArray())
                }
            }

            val response = Response(
                connection = connection,
                body = connection.inputStream.bufferedReader()
            )

            connection.disconnect()
            return response
        }.getOrElse {
            return Response(exception = it)
        }

    /**
     * Executes an HTTP request synchronously and parses the response as JSON.
     *
     * @param T The type of the expected JSON response.
     */
    inline fun <reified T: Any> json(): T? =
        doRequest().body?.let { Lambda.gson.fromJson(it, T::class.java) }
}
