package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Party

fun leaveParty(
    endpoint: String,
    version: String,
    accessToken: String,
) =
    Request(
        "$endpoint/api/$version/party/leave",
        Method.PUT,
        headers =
            mapOf(
                "Authorization" to "Bearer $accessToken"
            )
    ).json<Party>()

