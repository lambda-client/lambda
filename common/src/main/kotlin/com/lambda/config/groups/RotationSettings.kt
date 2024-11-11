/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.interaction.rotation.RotationMode
import kotlin.math.*
import kotlin.random.Random

class RotationSettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : IRotationConfig {
    /**
     * The rotation mode
     */
    override var rotationMode by c.setting(
        "Mode",
        RotationMode.SYNC,
        "SILENT - server-side rotation, SYNC - server-side rotation; client-side movement, LOCK - Lock camera",
        vis
    )

    /**
     * How many ticks to keep the rotation before resetting
     */
    override val keepTicks by c.setting("Keep Rotation", 3, 0..10, 1, "Ticks to keep rotation", " ticks", vis)

    /**
     * How many ticks to wait before resetting the rotation
     */
    override val resetTicks by c.setting("Reset Rotation", 3, 1..10, 1, "Ticks before rotation is reset", " ticks", vis)

    /**
     * Whether the rotation is instant
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
     * We must always provide turn speed to the interpolator because the player's yaw might exceed the -180 to 180 range.
     * Therefore, we cannot simply assign new angles to the player's rotation without getting flagged by Grim's AimModulo360 check.
     */
    override val turnSpeed get() = if (instant) 180.0 else abs(mean + spread * nextRandom())

    var speedMultiplier = 1.0

    private fun nextRandom(): Double {
        val u1 = Random.nextDouble()
        val u2 = Random.nextDouble()

        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
