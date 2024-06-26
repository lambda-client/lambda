package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object LambdaMoji : Module(
    name = "LambdaMoji",
    description = "",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.RENDER),
    enabledByDefault = true,
)
