package com.lambda.interaction.rotation

import com.lambda.interaction.RotationManager.currentRotation
import com.lambda.interaction.RotationManager.prevRotation
import com.lambda.interaction.rotation.Rotation.Companion.fixSensitivity

data class RotationContext(
    val rotation: Rotation,
    val config: IRotationConfig,
) {
    val isPending: Boolean get() = rotation.fixSensitivity(prevRotation) == currentRotation
}