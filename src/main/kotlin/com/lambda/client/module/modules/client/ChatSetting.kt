package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module

object ChatSetting : Module(
    name = "ChatSetting",
    category = Category.CLIENT,
    description = "Configures chat message manager",
    showOnArray = false,
    alwaysEnabled = true
) {
    val delay = setting("Message Speed Limit", 0.5f, 0.1f..20.0f, 0.1f, description = "Delay between each message in seconds")
    val maxMessageQueueSize = setting("Max Message Queue Size", 50, 10..200, 5)
}