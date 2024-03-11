package com.lambda.util

import com.lambda.Lambda.mc
import java.io.File

object FolderRegister {
    val minecraft: File = mc.runDirectory
    val lambda: File = File(minecraft, "lambda")
    val config: File = File(lambda, "config")
}
