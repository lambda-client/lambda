package com.lambda.interaction.rotation

import net.minecraft.util.hit.HitResult

data class RotationContext(
    val rotation: Rotation,
    val config: IRotationConfig,
    val hitResult: HitResult? = null,
    val verify: HitResult.() -> Boolean = { true },
) {
    val isValid: Boolean get() = hitResult?.verify() == true
}