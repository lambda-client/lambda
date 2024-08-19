package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.http.request

fun createParty(
    endpoint: String,
    version: String,
    accessToken: String,
) =
    request("$endpoint/api/$version/party") {
        method(Method.POST)

        headers(
            mapOf("Authorization" to "Bearer $accessToken")
        )
    }.json<Party>()
