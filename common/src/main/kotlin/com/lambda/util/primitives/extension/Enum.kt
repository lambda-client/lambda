package com.lambda.util.primitives.extension

import com.lambda.util.Nameable
import java.util.*

val Enum<*>.displayValue get() = (this as? Nameable)?.name ?: this.name.split('_').joinToString(" ") { low ->
    low.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}