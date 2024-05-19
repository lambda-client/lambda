package com.lambda.http.api.rpc.v1.endpoints

import com.lambda.http.Method
import com.lambda.http.Request
import com.lambda.http.api.rpc.v1.models.Party

fun editParty(
    endpoint: String,
    version: String,
    accessToken: String,

    // The maximum number of players in the party.
    // example: 10
    maxPlayers: Int = 10,

    // Whether the party is public or not.
    // If false can only be joined by invite.
    // example: true
    public: Boolean = true,
) = Request(
    "$endpoint/api/$version/party/edit",
    Method.PATCH,
    parameters =
        mapOf(
            "max_players" to maxPlayers,
            "public" to public,
        ),
    headers =
        mapOf(
            "Authorization" to "Bearer $accessToken"
        )
    ).json<Party>()
