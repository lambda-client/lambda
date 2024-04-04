package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object XRay : Module(
    name = "XRay",
    description = "Allows you to see ores through walls",
    defaultTags = setOf(ModuleTag.RENDER)
) {

}