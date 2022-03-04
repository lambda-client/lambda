package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module

object Plugins : Module(
    name = "Plugins",
    description = "Config for plugins",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysEnabled = true
) {
    private val tokenSetting by setting("Github Token", "")
    // ToDo: Add setting for other remote repositories here and save load status of plugins

    val token get() = tokenSetting
}