package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object HUD : Module(
    name = "HUD",
    description = "Visual behaviour configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val scale by setting("Scale", 2.0, 0.5..4.0, 0.01, description = "UI Scale factor")
}