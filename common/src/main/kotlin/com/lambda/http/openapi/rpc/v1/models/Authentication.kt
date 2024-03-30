package com.lambda.http.openapi.rpc.v1.models

data class Authentication(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String,
    val message: String,
)
