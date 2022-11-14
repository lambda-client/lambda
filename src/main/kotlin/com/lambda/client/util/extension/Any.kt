package com.lambda.client.util.extension

inline fun <reified T : Any> Any?.ifType(block: (T) -> Unit) {
    if (this is T) block(this)
}


