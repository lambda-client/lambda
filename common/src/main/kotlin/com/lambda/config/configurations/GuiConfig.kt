package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister
import java.io.File

object GuiConfig : Configuration() {
    override val configName get() = "gui"
    override val primary: File = FolderRegister.config.resolve("$configName.json").toFile()
}
