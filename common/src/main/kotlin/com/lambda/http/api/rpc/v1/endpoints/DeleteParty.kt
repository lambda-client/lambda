package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Party

fun deleteParty(
    endpoint: String,
    version: String,
    accessToken: String,
) =
    Request(
        "$endpoint/api/$version/party/delete",
        Method.DELETE,
        headers =
            mapOf(
                "Authorization" to "Bearer $accessToken"
            )
    ).json<Party>()

