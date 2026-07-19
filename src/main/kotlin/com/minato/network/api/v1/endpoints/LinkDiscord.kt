
package com.minato.network.api.v1.endpoints

import com.minato.network.MINATO_HTTP
import com.minato.network.MinatoAPI.apiUrl
import com.minato.network.MinatoAPI.apiVersion
import com.minato.network.NetworkHandler
import com.minato.network.api.v1.models.Authentication
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Links a Discord account to a session account
 *
 * Example:
 *  - token: OTk1MTU1NzcyMzYxMTQ2NDM4
 */
suspend fun linkDiscord(discordToken: String) = runCatching {
    MINATO_HTTP.post("${apiUrl}/api/$apiVersion/link/discord") {
        setBody("""{ "token": "$discordToken" }""")
        bearerAuth(NetworkHandler.accessToken)
        contentType(ContentType.Application.Json)
    }.body<Authentication>()
}
