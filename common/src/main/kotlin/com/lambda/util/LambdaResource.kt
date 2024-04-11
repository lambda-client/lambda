package com.lambda.util

class LambdaResource(val path: String) {
    val stream get() =
        javaClass.getResourceAsStream("/assets/lambda/$path")
}