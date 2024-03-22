package com.lambda.config

import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationMode
import kotlin.random.Random

class RotationSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : IRotationConfig {
    override val rotationMode by c.setting("Mode", RotationMode.LOCK, "SILENT - server-side rotation, SYNC - server-side rotation; client-side movement, LOCK - Lock camera", vis)
    override val keepTicks by c.setting("Keep Rotation", 3, 1..10, 1, "Ticks to keep rotation", vis)
    override val resetTicks by c.setting("Reset Rotation", 3, 1..10, 1, "Ticks before rotation is reset", vis)

    private val r1 by c.setting("Turn Speed 1", 70.0, 1.0..180.0, 0.1, "Rotation Speed 1", vis)
    private val r2 by c.setting("Turn Speed 2", 110.0, 1.0..180.0, 0.1, "Rotation Speed 2", vis)

    override val turnSpeed get() = Random.nextDouble(r1, r2)

    var speedMultiplier = 1.0

    fun slowdownIf(flag: Boolean) {
        speedMultiplier = (if (flag) 0.0 else 1.0)
            .coerceIn(
                speedMultiplier - 0.3, // slowdown faster
                speedMultiplier + 0.15 // accelerate slower
            )
    }
}