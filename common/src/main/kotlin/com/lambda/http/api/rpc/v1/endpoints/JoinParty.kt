package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Request
import com.lambda.http.Method
import com.lambda.http.api.rpc.v1.models.Party

fun joinParty(
    endpoint: String,
    version: String,
    accessToken: String,
    partyId: String,
) =
    Request(
        "$endpoint/api/$version/party/join",
        Method.PUT,
        parameters =
            mapOf(
                "partyId" to partyId
            ),
        headers =
            mapOf(
                "Authorization" to "Bearer $accessToken"
            )
    ).json<Party>()
