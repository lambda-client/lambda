/*
 * Copyright 2026 Lambda
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

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.manager.managers.rotating.RotationMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class RotationSettings(override val c: Config) : RotationConfig, ConfigBlock {
    override var rotationMode by c.setting("Mode", RotationMode.Sync, "How the player is being rotated on interaction")

    /** How many ticks to keep the rotation before resetting */
    override val keepTicks by c.setting("Keep Rotation", 1, 1..10, 1, "Ticks to keep rotation", " ticks")

    /** How many ticks to wait before resetting the rotation */
    override val decayTicks by c.setting("Reset Rotation", 1, 1..10, 1, "Ticks before rotation is reset", " ticks")

    override val tickStageMask = ALL_STAGES.subList(0, ALL_STAGES.indexOf(TickEvent.Player.Post)).toSet()

    /** Whether the rotation is instant */
    var instant by c.setting("Instant Rotation", true, "Instantly rotate")

    /**
     * The mean (average/base) value used to calculate rotation speed.
     * This value represents the center of the distribution.
     */
    var mean by c.setting("Mean", 40.0, 1.0..120.0, 0.1, "Average rotation speed", unit = "°") { !instant }

    /**
     * The standard deviation for the Gaussian distribution used to calculate rotation speed.
     * This value represents the spread of rotation speed.
     */
    var spread by c.setting("Spread", 10.0, 0.0..60.0, 0.1, "Spread of rotation speeds", unit = "°") { !instant }

    /**
     * We must always provide turn speed to the interpolator because the player's yaw might exceed the -180 to 180 range.
     * Therefore, we cannot simply assign new angles to the player's rotation without getting flagged by Grim's AimModulo360 check.
     */
    override val turnSpeed get() = if (instant) 180.0 else abs(mean + spread * nextGaussian())

    private fun nextGaussian(): Double {
        val u1 = Random.nextDouble()
        val u2 = Random.nextDouble()

        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
