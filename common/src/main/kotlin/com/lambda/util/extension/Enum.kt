package com.lambda.util.extension

import com.lambda.util.Nameable

val Enum<*>.displayValue
    get() =
        (this as? Nameable.NamedEnum)?.displayName ?: name.split('_').joinToString(" ") { low ->
            low.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
