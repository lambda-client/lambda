package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.graphics.Shaders

internal object MenuShader : Module(
    name = "MenuShader",
    description = "Shows a shader on the main menu",
    showOnArray = false,
    category = Category.CLIENT
) {
    val mode by setting("Mode", Mode.Random)
    val shader by setting("Shader", Shaders.TRIANGLE, {mode == Mode.Set})
    
    enum class Mode {
        Random, Set
    }
}