package com.lambda.interaction

import com.lambda.interaction.rotation.Rotation
import net.minecraft.util.math.Vec3d

data class MovementConfiguration(
    var position: Vec3d,
    var rotation: Rotation,
    var onGround: Boolean,
    var sprinting: Boolean,
    var sneaking: Boolean,
)
