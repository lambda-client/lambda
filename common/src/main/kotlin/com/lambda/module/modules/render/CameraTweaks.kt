package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object CameraTweaks : Module(
    name = "CameraTweaks",
    description = "Adjusts camera settings",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    @JvmStatic val camDistance by setting("Camera Distance", 4.0, 1.0..20.0, 0.1)
    @JvmStatic val noClipCam by setting("No Clip Camera", true)
}