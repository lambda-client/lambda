package com.lambda.util.extension

val Class<*>.isObject: Boolean
    get() = declaredFields.any { it.name == "INSTANCE" }

val Class<*>.objectInstance: Any
    get() = declaredFields.first { it.name == "INSTANCE" }.apply { isAccessible = true }.get(null)
