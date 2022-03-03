package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder

object GuiColors : Module(
    name = "GuiColors",
    description = "Customize gui colors",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysEnabled = true
) {
    private val primarySetting by setting("Primary Color", ColorHolder(108, 0, 43, 255))
    private val outlineSetting by setting("Outline Color", ColorHolder(78, 0, 31, 200))
    private val backgroundSetting by setting("Background Color", ColorHolder(31, 10, 18, 235))
    private val textSetting by setting("Text Color", ColorHolder(255, 205, 225, 255))
    private val aHover by setting("Hover Alpha", 32, 0..255, 1)

    val primary get() = primarySetting.clone()
    val idle get() = if (primary.averageBrightness < 0.8f) ColorHolder(255, 255, 255, 0) else ColorHolder(0, 0, 0, 0)
    val hover get() = idle.apply { a = aHover }
    val click get() = idle.apply { a = aHover * 2 }
    val backGround get() = backgroundSetting.clone()
    val outline get() = outlineSetting.clone()
    val text get() = textSetting.clone()
}
