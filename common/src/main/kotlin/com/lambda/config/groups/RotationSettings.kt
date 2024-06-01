package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.interaction.rotation.RotationMode
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RotationSettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : IRotationConfig {
    override var rotationMode by c.setting("Mode", RotationMode.SYNC, "SILENT - server-side rotation, SYNC - server-side rotation; client-side movement, LOCK - Lock camera", vis)
    override val keepTicks by c.setting("Keep Rotation", 3, 1..10, 1, "Ticks to keep rotation", " ticks", vis)
    override val resetTicks by c.setting("Reset Rotation", 3, 1..10, 1, "Ticks before rotation is reset", " ticks", vis)

    var r1 by c.setting("Turn Speed 1", 70.0, 1.0..180.0, 0.1, "Rotation Speed 1", "", vis)
    var r2 by c.setting("Turn Speed 2", 110.0, 1.0..180.0, 0.1, "Rotation Speed 2", "", vis)

    override val turnSpeed get() = Random.nextDouble(min(r1, r2), max(r1, r2) + 0.01)

    var speedMultiplier = 1.0

    fun slowdownIf(flag: Boolean) {
        speedMultiplier = (if (flag) 0.0 else 1.0)
            .coerceIn(
                speedMultiplier - 0.3, // slowdown faster
                speedMultiplier + 0.15 // accelerate slower
            )
    }
}