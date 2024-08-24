package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Request
import com.lambda.http.Method
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.http.request

fun joinParty(
    endpoint: String,
    version: String,
    accessToken: String,

    // The ID of the party.
    // example: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
    partyId: String,
) =
    request("$endpoint/api/$version/party/join") {
        method(Method.PUT)

        parameters(
            mapOf(
                "id" to partyId,
            )
        )

        headers(
            mapOf("Authorization" to "Bearer $accessToken")
        )
    }.json<Party>()
