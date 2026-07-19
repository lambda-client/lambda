
package com.minato.network.api.v1.endpoints

import com.minato.network.MINATO_HTTP
import com.minato.network.MinatoAPI.apiUrl
import com.minato.network.MinatoAPI.apiVersion
import com.minato.network.api.v1.models.Authentication
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Creates a new session account with mojang session hashes
 *
 * Example:
 *  - username: Notch
 *  - hash: 069a79f444e94726a5befca90e38aaf5
 */
suspend fun login(username: String, hash: String) = runCatching {
    MINATO_HTTP.post("${apiUrl}/api/$apiVersion/login") {
        setBody("""{ "username": "$username", "hash": "$hash" }""")
        contentType(ContentType.Application.Json)
    }.body<Authentication>()
}
