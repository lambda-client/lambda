package com.lambda

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister

object LambdaConfig : Configuration() {
    override val configName = "lambda"
    override val primary = FolderRegister.lambda.resolve("$configName.json")
}