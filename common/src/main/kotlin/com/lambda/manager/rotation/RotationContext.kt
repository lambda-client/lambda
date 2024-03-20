package com.lambda.manager.rotation

data class RotationContext(
    val config: IRotationConfig,
    val rotation: Rotation
)