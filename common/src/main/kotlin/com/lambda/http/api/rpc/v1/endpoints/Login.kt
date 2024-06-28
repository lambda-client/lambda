package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Authentication

fun login(
    endpoint: String,
    version: String,

    // The player's Discord token.
    // example: OTk1MTU1NzcyMzYxMTQ2NDM4
    discordToken: String,

    // The player's username.
    // example: "Notch"
    username: String,

    // The player's Mojang session hash.
    // example: 069a79f444e94726a5befca90e38aaf5
    hash: String
) =
    Request(
        "$endpoint/api/$version/login",
        Method.POST,
        parameters =
            mapOf(
                "token" to discordToken,
                "username" to username,
                "hash" to hash
            )
    ).json<Authentication>()
