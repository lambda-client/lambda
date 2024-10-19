package com.lambda.http

import com.lambda.Lambda
import com.lambda.util.FolderRegister.cache
import com.lambda.util.FolderRegister.createIfNotExists
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Represents an HTTP request.
 *
 * @property url The URL to which the request will be made.
 * @property method The HTTP method to be used for the request. Default is [Method.GET].
 * @property parameters A map of query parameters to be included in the request. Default is an empty map.
 * @property headers A map of headers to be included in the request. Default is an empty map.
 * @property config A lambda function to configure the HTTP connection. Default is an empty lambda.
 */
data class Request(
    val url: String,
    val method: Method = Method.GET,
    val parameters: Map<String, Any> = mapOf(),
    val headers: Map<String, String> = mapOf(),
    val config: ((HttpURLConnection) -> Unit) = {},
) {
    val exceptionFormat = "HTTP request failed with status code %d\nResponse: %s"

    val canBeEncoded: Boolean
        get() = method != Method.POST && method != Method.PUT && method != Method.PATCH

    /**
     * Downloads the resource at the specified path and caches it for future use.
     *
     * @param name The full name of the file to be cached.
     * @param maxAge The maximum age of the cached resource. Default is 4 days.
     *
     * @return A pair containing the cached file and a boolean indicating whether the file was downloaded.
     */
    fun maybeDownload(
        name: String,
        maxAge: Duration = 7.days,
    ): File {
        val file = cache.resolve(name).createIfNotExists()

        if (
            System.currentTimeMillis() - file.lastModified() < maxAge.inWholeMilliseconds
            && file.length() > 0
        ) return file

        file.writeText("") // Clear the file before writing to it.

        val url = URL(
            if (parameters.isNotEmpty() && canBeEncoded) "$url?${parameters.query}"
            else url
        )

        val connection = url.openConnection() as HttpURLConnection
        config.invoke(connection)

        connection.requestMethod = method.name

        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

        connection.connect()

        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file
    }

    /**
     * Executes the HTTP request synchronously.
     */
    inline fun <reified Success: Any> json(): Response<Success> {
        val url = URL(
            if (parameters.isNotEmpty() && canBeEncoded) "$url?${parameters.query}"
            else url
        )

        val connection = url.openConnection() as HttpURLConnection
        config.invoke(connection)

        connection.requestMethod = method.name

        headers.forEach { (key, value) -> connection.setRequestProperty(key, Lambda.gson.toJson(value)) }

        // For the moment we are only supporting JSON requests.
        connection.setRequestProperty("Content-Type", "application/json")

        runCatching {
            if (!canBeEncoded) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(parameters.toJson().toByteArray())
                }
            } else connection.connect()
        }.onFailure {
            println("Failed to execute HTTP request: $it")
            return Response(
                connection = connection,
                data = null,
                error = it
            )
        }

        if (connection.responseCode !in 200..299) {
            return Response(
                connection = connection,
                data = null,
                error = Throwable(
                    exceptionFormat.format(
                        connection.responseCode,
                        tryOrDefault(
                            "A critical error causes the remote client to abruptly close the connection.\n" +
                                    "No action is required on your side."
                        ) {
                            connection.errorStream.bufferedReader().readText()
                        }
                    )
                )
            )
        }

        var error: Throwable? = null
        val data = runCatching { Lambda.gson.fromJson(connection.inputStream.bufferedReader().readText(), Success::class.java) }
            .onFailure { error = it }
            .getOrNull()

        return Response(
            connection = connection,
            data = data,
            error = error,
        )
    }
}
