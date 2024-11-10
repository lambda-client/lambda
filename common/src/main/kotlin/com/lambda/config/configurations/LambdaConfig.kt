package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister
import java.io.File

object LambdaConfig : Configuration() {
    override val configName get() = "lambda"
    override val primary: File = FolderRegister.config.resolve("$configName.json").toFile()
}
