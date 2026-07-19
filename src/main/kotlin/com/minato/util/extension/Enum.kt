
package com.minato.util.extension

import com.minato.util.NamedEnum
import com.minato.util.StringUtils.capitalize

val Enum<*>.displayValue
    get() =
        (this as? NamedEnum)?.displayName ?: name.split('_').joinToString(" ") { low ->
            low.capitalize()
        }
