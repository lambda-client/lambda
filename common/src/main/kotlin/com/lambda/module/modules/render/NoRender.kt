package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object NoRender : Module(
    name = "NoRender",
    description = "Disables rendering of certain things",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    @JvmStatic val noDarkness by setting("No Darkness", true)
    @JvmStatic val noBurning by setting("No Burning Overlay", true)
    @JvmStatic val fireOverlayYOffset by setting("Fire Overlay Y Offset", -0.3, -0.8..0.0, 0.1) { !noBurning }
    @JvmStatic val noUnderwater by setting("No Underwater Overlay", true)
    @JvmStatic val noInWall by setting("No In Wall Overlay", true)
}