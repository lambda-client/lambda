package com.lambda.interaction.rotation

import com.lambda.config.groups.IRotationConfig
import net.minecraft.util.hit.HitResult

data class RotationContext(
    val rotation: Rotation,
    val config: IRotationConfig,
    val hitResult: HitResult? = null,
    val verify: HitResult.() -> Boolean = { true },
) {
    val isValid: Boolean get() = hitResult?.verify() == true
}