package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister

object LambdaConfig : Configuration() {
    override val configName get() = "lambda"
    override val primary = FolderRegister.config.resolve("$configName.json")
}
