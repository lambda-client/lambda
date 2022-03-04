package com.lambda.client.setting

import com.lambda.client.module.AbstractModule
import com.lambda.client.module.modules.client.Configurations
import com.lambda.client.setting.configs.NameableConfig
import com.lambda.client.util.FolderUtils
import java.io.File

internal object ModuleConfig : NameableConfig<AbstractModule>(
    "modules",
    "${FolderUtils.lambdaFolder}config/modules",
) {
    override val file: File get() = File("$filePath/${Configurations.modulePreset}.json")
    override val backup get() = File("$filePath/${Configurations.modulePreset}.bak")
}