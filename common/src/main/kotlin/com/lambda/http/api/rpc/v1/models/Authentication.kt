package com.lambda.http.api.rpc.v1.models

// Authentication represents the response from the authentication endpoint.
data class Authentication(
    // The access token to use for the API
    // example: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
    val accessToken: String,

    // The duration of the token (in seconds).
    // example: 3600
    val expiresIn: Long,

    // The type of the token.
    // example: Bearer
    val tokenType: String,
)
