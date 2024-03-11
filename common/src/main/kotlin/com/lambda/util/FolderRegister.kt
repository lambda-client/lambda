package com.lambda.util

import dev.architectury.platform.Platform
import java.io.File

object FolderRegister {
    val minecraft: File = Platform.getGameFolder().toFile()
    val lambda: File = File(minecraft, "lambda")
    val config: File = File(lambda, "config")
}
