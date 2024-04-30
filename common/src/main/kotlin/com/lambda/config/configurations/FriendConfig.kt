package com.lambda.config.configurations

import com.lambda.config.Configuration
import com.lambda.util.FolderRegister

object FriendConfig : Configuration() {
    override val configName = "friends"
    override val primary = FolderRegister.config.resolve("$configName.json")
}
