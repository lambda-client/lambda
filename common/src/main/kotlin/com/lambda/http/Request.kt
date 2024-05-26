package com.lambda.http

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import java.net.HttpURLConnection
import java.net.URL

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
    private val exceptionFormat = "HTTP request failed with status code %d\nResponse: %s"

    private val canBeEncoded: Boolean
        get() = method != Method.POST && method != Method.PUT && method != Method.PATCH

    /**
     * Executes the HTTP request synchronously.
     */
    fun doRequest(): Response {
        val url = URL(
            if (parameters.isNotEmpty() && canBeEncoded) "$url?${parameters.query}"
            else url
        )

        val connection = url.openConnection() as HttpURLConnection
        config.invoke(connection)

        connection.requestMethod = method.value

        headers.forEach { (key, value) -> connection.setRequestProperty(key, Lambda.gson.toJson(value)) }

        // For the moment we are only supporting JSON requests.
        connection.setRequestProperty("Content-Type", "application/json")

        if (!canBeEncoded) {
            connection.doOutput = true
            connection.outputStream.use {
                it.write(parameters.toJson().toByteArray())
            }
        }

        connection.connect()

        if (connection.responseCode !in 200..299) {
            return Response(
                connection = connection,
                exception = Throwable(
                    exceptionFormat.format(
                        connection.responseCode,
                        tryOrDefault(
                            connection.errorStream.bufferedReader().readText()
                        ) { "A critical error causes the remote client to abruptly close the connection.\n" +
                                "No action is required on your side." }
                    )
                )
            )
        }

        val response = Response(
            connection = connection,
            body = connection.inputStream.bufferedReader()
        )

        return response
    }

    /**
     * Executes an HTTP request synchronously and parses the response as JSON.
     *
     * @param T The type of the expected JSON response.
     */
    inline fun <reified T : Any> json(): T? {
        val response = doRequest()

        response.exception?.let {
            LOG.error(it)
            return null
        }

        return response.body?.let { Lambda.gson.fromJson(it, T::class.java) }
    }
}
