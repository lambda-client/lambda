
package com.minato.util.extension

import com.mojang.authlib.GameProfile
import java.io.File
import java.nio.file.Path

val GameProfile.isOffline
    get() = properties.isEmpty

val Class<*>.isObject: Boolean
    get() = declaredFields.any { it.name == "INSTANCE" }

val Class<*>.objectInstance: Any
    get() = declaredFields.first { it.name == "INSTANCE" }.apply { isAccessible = true }.get(null)

fun Path.resolveFile(other: String): File =
    resolve(other).toFile()
