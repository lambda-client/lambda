package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.interaction.rotation.RotationMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class RotationSettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : IRotationConfig {
    override var rotationMode by c.setting("Mode", RotationMode.SYNC, "SILENT - server-side rotation, SYNC - server-side rotation; client-side movement, LOCK - Lock camera", vis)
    override val keepTicks by c.setting("Keep Rotation", 3, 1..10, 1, "Ticks to keep rotation", " ticks", vis)
    override val resetTicks by c.setting("Reset Rotation", 3, 1..10, 1, "Ticks before rotation is reset", " ticks", vis)
    override var mean by c.setting("Mean", 90.0, 1.0..180.0, 0.1, "Average rotation speed", "", vis)
    override var derivation by c.setting("Standard Deviation", 10.0, 0.0..100.0, 0.1, "Spread of rotation speeds", "", vis)

    override val turnSpeed get() = nextGaussian(mean, derivation)

    var speedMultiplier = 1.0

    private fun nextGaussian(mean: Double = 0.0, standardDeviation: Double = 1.0): Double {
        val u1 = Random.nextDouble()
        val u2 = Random.nextDouble()

        val randStdNormal = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return mean + standardDeviation * randStdNormal
    }
}