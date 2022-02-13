package com.lambda.client.commons.extension

/**
 * Limit the length of this string to [max]
 */
fun String.max(max: Int) = this.substring(0, this.length.coerceAtMost(max))

/**
 * Limit the length to this string [max] with [suffix] appended
 */
fun String.max(max: Int, suffix: String): String {
    return if (this.length > max) {
        this.max(max - suffix.length) + suffix
    } else {
        this.max(max)
    }
}

fun String.surroundedBy(prefix: CharSequence, suffix: CharSequence, ignoreCase: Boolean = false) =
    this.startsWith(prefix, ignoreCase) && this.endsWith(suffix, ignoreCase)

fun String.surroundedBy(prefix: Char, suffix: Char, ignoreCase: Boolean = false) =
    this.startsWith(prefix, ignoreCase) && this.endsWith(suffix, ignoreCase)

fun String.mapEach(vararg delimiters: Char, transformer: (String) -> String) =
    split(*delimiters).map(transformer)