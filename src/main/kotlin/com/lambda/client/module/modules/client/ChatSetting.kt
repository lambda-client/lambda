package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module

object ChatSetting : Module(
    name = "ChatSetting",
    description = "Configures chat message manager",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysEnabled = true
) {
    val delay by setting("Message Speed Limit", 0.5f, 0.1f..20.0f, 0.1f, description = "Delay between each message", unit = "s")
    val maxMessageQueueSize by setting("Max Message Queue Size", 50, 10..200, 5)
}