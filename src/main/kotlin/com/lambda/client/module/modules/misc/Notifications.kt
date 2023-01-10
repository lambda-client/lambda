package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module

object Notifications : Module(
    name = "Notifications",
    description = "Show chat notifications when toggling a module",
    category = Category.MISC,
    enabledByDefault = true
) {
}
