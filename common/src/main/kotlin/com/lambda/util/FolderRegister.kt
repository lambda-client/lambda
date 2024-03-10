package com.lambda.util

import java.io.File

object FolderRegister {
    val minecraft: File = File("") // Absolute path to .minecraft
    val lambda: File = File(minecraft, "lambda")
    val config: File = File(lambda, "config")
}
