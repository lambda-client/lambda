package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module

object Tooltips : Module(
    name = "Tooltips",
    description = "Displays handy module descriptions in the GUI",
    category = Category.CLIENT,
    showOnArray = false,
    enabledByDefault = true
)
