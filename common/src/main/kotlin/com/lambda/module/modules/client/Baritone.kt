package com.lambda.module.modules.client

import com.lambda.config.groups.RotationSettings
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Baritone : Module(
    name = "Baritone",
    description = "Baritone configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val rotation = RotationSettings(this)
}