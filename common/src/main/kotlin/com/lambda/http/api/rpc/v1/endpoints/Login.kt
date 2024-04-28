package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Authentication

fun login(
    endpoint: String,
    version: String,
    discordToken: String,
    username: String,
    hash: String
) =
    Request(
        "$endpoint/api/$version/party/login",
        Method.POST,
        parameters =
            mapOf(
                "token" to "Bearer $discordToken",
                "username" to username,
                "hash" to hash
            )
    ).json<Authentication>()
