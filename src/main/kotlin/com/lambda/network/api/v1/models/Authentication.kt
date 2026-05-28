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

package com.lambda.network.api.v1.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Authentication(
    @JsonProperty("access_token")
    val accessToken: String,

    @JsonProperty("expires_in")
    val expiresIn: Long,

    @JsonProperty("token_type")
    val tokenType: String,
) {
    data class Data(
        @JsonProperty("nbf")
        val notBefore: Long,

        @JsonProperty("iat")
        val issuedAt: Long,

        @JsonProperty("exp")
        val expirationDate: Long,

        @JsonProperty("data")
        val data: Player,
    )
}
