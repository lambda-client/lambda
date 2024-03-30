package com.lambda.http.openapi.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.openapi.rpc.v1.models.Authentication

fun login(
    endpoint: String,
    version: String,
    accessToken: String,
    username: String,
    hash: String
) =
    Request(
        "$endpoint/api/$version/party/login",
        Method.POST,
        mapOf(
            "token" to "Bearer $accessToken",
            "username" to username,
            "hash" to hash
        )
    ).json<Authentication>()
