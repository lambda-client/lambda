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

package com.lambda.network.api.v1.endpoints

import com.lambda.network.LAMBDA_HTTP
import com.lambda.network.LambdaAPI.apiUrl
import com.lambda.network.LambdaAPI.apiVersion
import com.lambda.network.api.v1.models.Authentication
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
    LAMBDA_HTTP.post("${apiUrl}/api/$apiVersion/login") {
        setBody("""{ "username": "$username", "hash": "$hash" }""")
        contentType(ContentType.Application.Json)
    }.body<Authentication>()
}
