package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister
import java.io.File

object FriendConfig : Configuration() {
    override val configName get() = "friends"
    override val primary: File = FolderRegister.config.resolve("$configName.json").toFile()
}
