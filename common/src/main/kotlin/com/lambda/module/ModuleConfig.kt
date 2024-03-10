package com.lambda.module

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister
import java.io.File

object ModuleConfig : Configuration() {
    override val configName = "modules"
    override val primary = File(FolderRegister.config, "$configName.json")
}