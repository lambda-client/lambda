package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.interaction.rotation.RotationMode
import kotlin.math.*
import kotlin.random.Random

class RotationSettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : IRotationConfig {
    override var rotationMode by c.setting(
        "Mode",
        RotationMode.SYNC,
        "SILENT - server-side rotation, SYNC - server-side rotation; client-side movement, LOCK - Lock camera",
        vis
    )

    override val keepTicks by c.setting("Keep Rotation", 3, 1..10, 1, "Ticks to keep rotation", " ticks", vis)
    override val resetTicks by c.setting("Reset Rotation", 3, 1..10, 1, "Ticks before rotation is reset", " ticks", vis)

    /**
     * If true, rotation will be instant without any transition. If false, rotation will transition over time.
     */
    var instant by c.setting("Instant Rotation", true, "Instantly rotate", vis)

    /**
     * The mean (average/base) value used to calculate rotation speed.
     * This value represents the center of the distribution.
     */
    private var mean by c.setting(
        "Mean",
        40.0,
        1.0..120.0,
        0.1,
        "Average rotation speed",
        unit = "°"
    ) { vis() && !instant }

    /**
     * The standard deviation for the Gaussian distribution used to calculate rotation speed.
     * This value represents the spread of rotation speed.
     */
    private var spread by c.setting(
        "Spread",
        10.0,
        0.0..60.0,
        0.1,
        "Spread of rotation speeds",
        unit = "°"
    ) { vis() && !instant }

    /**
     * We always have to pass turn speed to the interpolator, because player's yaw could be out of -180..180 range and
     * Thus we cant simply assign new angles to the player's rotation without getting flagged by Grim's AimModulo360 check
     */
    override val turnSpeed get() = if (instant) 180.0 else abs(mean + spread * nextRandom())

    var speedMultiplier = 1.0

    private fun nextRandom(): Double {
        val u1 = Random.nextDouble()
        val u2 = Random.nextDouble()

        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}