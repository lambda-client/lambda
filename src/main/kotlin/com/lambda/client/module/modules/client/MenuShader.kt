package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.graphics.Shaders

internal object MenuShader : Module(
    name = "MenuShader",
    description = "Shows a shader on the main menu",
    showOnArray = false,
    category = Category.CLIENT,
    enabledByDefault = true
) {
    val mode by setting("Mode", Mode.SET)
    val shader by setting("Shader", Shaders.CLOUDS, { mode == Mode.SET })
    
    enum class Mode {
        RANDOM, SET
    }
}