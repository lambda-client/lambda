package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module

internal object CleanGUI : Module(
    name = "CleanGUI",
    category = Category.RENDER,
    showOnArray = false,
    description = "Modifies parts of the GUI to be transparent"
) {
    val inventoryGlobal = setting("Inventory", true)
    val chatGlobal = setting("Chat", false)
}