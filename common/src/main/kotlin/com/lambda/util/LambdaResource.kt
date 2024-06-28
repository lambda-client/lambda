package com.lambda.util

import java.io.InputStream

class LambdaResource(val path: String) {
    val stream: InputStream?
        get() = javaClass.getResourceAsStream("/assets/lambda/$path")
}
