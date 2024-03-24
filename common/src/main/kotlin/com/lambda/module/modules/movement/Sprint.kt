package com.lambda.module.modules.movement

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Sprint : Module(
    name = "Sprint",
    description = "Sprints automatically",
    defaultTags = setOf(ModuleTag.MOVEMENT)
)