
package com.minato.network.api.v1.models

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
