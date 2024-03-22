package com.lambda.module.modules.client

import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.MathUtils.random

object Baritone : Module(
    name = "Baritone",
    description = "Baritone configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val r1 by setting("Turn Speed 1", 70.0, 1.0..180.0, 0.1)
    private val r2 by setting("Turn Speed 2", 110.0, 1.0..180.0, 0.1)

    val rotation = object : IRotationConfig {
        override val rotationMode = RotationMode.SYNC
        override val turnSpeed get() = random(r1, r2)
        override val keepTicks = 3
        override val resetTicks = 3
    }
}