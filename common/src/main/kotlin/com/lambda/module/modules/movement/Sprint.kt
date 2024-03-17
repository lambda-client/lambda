package com.lambda.module.modules.movement

import com.lambda.module.tag.ModuleTag
import com.lambda.module.Module

object Sprint : Module(
    name = "Sprint",
    description = "Sprints automatically",
    defaultTags = setOf(ModuleTag.MOVEMENT)
)