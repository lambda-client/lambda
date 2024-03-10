package com.lambda.common

import dev.architectury.injectables.annotations.ExpectPlatform
import org.jetbrains.annotations.Contract

object LoaderInfo {
    @Contract(pure = true)
    @ExpectPlatform
    @JvmStatic
    fun getVersion(): String = "DEV"
}
