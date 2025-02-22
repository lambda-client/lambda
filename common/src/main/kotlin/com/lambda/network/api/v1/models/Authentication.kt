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

package com.lambda.network.api.v1.models

import com.google.gson.annotations.SerializedName
import com.lambda.Lambda
import java.time.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Authentication(
    // The access token to use for the API
    // example: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
    @SerializedName("access_token")
    val accessToken: String,

    // The duration of the token (in seconds).
    // example: 3600
    @SerializedName("expires_in")
    val expiresIn: Long,

    // The type of the token.
    // example: Bearer
    @SerializedName("token_type")
    val tokenType: String,
) {
    @OptIn(ExperimentalEncodingApi::class)
    val decoded = Lambda.gson.fromJson(Base64.decode(accessToken).toString(), Payload::class.java)

    data class Payload(
        @SerializedName("nbf")
        val notBefore: Instant,

        @SerializedName("iat")
        val issuedAt: Instant,

        @SerializedName("exp")
        val expirationDate: Instant,

        @SerializedName("data")
        val data: Player,
    )
}
