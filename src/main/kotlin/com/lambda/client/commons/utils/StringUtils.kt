package com.lambda.client.commons.utils

inline fun buildString(block: StringBuilder.() -> Unit) =
    StringBuilder().apply(block).toString()

fun grammar(amount: Int, singular: String, plural: String): String {
    return if (amount == 1) {
        "$amount $singular"
    } else {
        "$amount $plural"
    }
}
