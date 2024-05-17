package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister

object GuiConfig : Configuration() {
    override val configName = "gui"
    override val primary = FolderRegister.config.resolve("$configName.json")
}
