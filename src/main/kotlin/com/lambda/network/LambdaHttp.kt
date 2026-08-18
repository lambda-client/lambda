/*
 * Copyright 2026 Lambda
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

@file:Suppress("unused")

package com.lambda.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

val LAMBDA_HTTP = HttpClient {
    install(ContentNegotiation) {
        gson()
    }
}

suspend inline fun HttpClient.download(url: String, file: File, block: HttpRequestBuilder.() -> Unit = {}) {
    val response = get(url, block)
    check(response.status.isSuccess()) { "Download for $url failed with non 2xx status code" }

    file.writeBytes(response.readRawBytes())
}

suspend inline fun HttpClient.download(url: String, output: OutputStream, block: HttpRequestBuilder.() -> Unit = {}) {
    val response = get(url, block)
    check(response.status.isSuccess()) { "Download for $url failed with non 2xx status code" }

	withContext(Dispatchers.IO) {
		output.write(response.readRawBytes())
	}
}

suspend inline fun HttpClient.download(url: String, block: HttpRequestBuilder.() -> Unit) =
    get(url, block).readRawBytes()

