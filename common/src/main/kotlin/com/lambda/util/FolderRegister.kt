package com.lambda.util

import com.lambda.Lambda.mc
import java.io.File

/**
 * The [FolderRegister] object is responsible for managing the directory structure of the application.
 *
 * @property minecraft The root directory of the Minecraft client. It is retrieved using the `runDirectory` property of the Minecraft client instance.
 * @property lambda The directory for the Lambda client, located within the Minecraft directory.
 * @property config The directory for storing configuration files, located within the Lambda directory.
 */
object FolderRegister {
    val minecraft: File = mc.runDirectory
    val lambda: File = File(minecraft, "lambda")
    val config: File = File(lambda, "config")
}
