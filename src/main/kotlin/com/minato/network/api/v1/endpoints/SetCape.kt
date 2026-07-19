
package com.minato.network.api.v1.endpoints

import com.minato.network.MINATO_HTTP
import com.minato.network.MinatoAPI.apiUrl
import com.minato.network.MinatoAPI.apiVersion
import com.minato.network.NetworkHandler
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Sets the currently authenticated player's cape
 *
 * Example:
 *  - id: galaxy
 */
suspend fun setCape(id: String) = runCatching {
    val resp = MINATO_HTTP.put("$apiUrl/api/$apiVersion/cape?id=$id") {
        bearerAuth(NetworkHandler.accessToken)
        contentType(ContentType.Application.Json)
    }

    check(resp.status == HttpStatusCode.OK)
}
