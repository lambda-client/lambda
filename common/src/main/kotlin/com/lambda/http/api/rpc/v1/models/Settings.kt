package com.lambda.http.api.rpc.v1.models

data class Settings(
    // The maximum number of players in the party.
    // example: 10
    val maxPlayers: Int,

    // Whether the party is public or not.
    // If false can only be joined by invite.
    // example: true
    val public: Boolean,

    // Whether the party can be listed or not.
    // example: true
    val listed: Boolean,
)
