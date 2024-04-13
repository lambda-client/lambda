package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object NoRender : Module(
    name = "NoRender",
    description = "Disables rendering of certain things",
    tag = ModuleTag.RENDER
) {
    @JvmStatic val noDarkness by setting("No Darkness", true)
}