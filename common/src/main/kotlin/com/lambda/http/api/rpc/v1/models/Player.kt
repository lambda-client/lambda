package com.lambda.http.api.rpc.v1.models

// Represents a player in a party.
data class Player (
    // The player's name.
    // example: Notch
    val name: String,

    // The player's UUID.
    // example: 069a79f4-44e9-4726-a5be-fca90e38aaf5
    val uuid: String,

    // The player's Discord ID.
    // example: "385441179069579265"
    val discordId: String,
)
