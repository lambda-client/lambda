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
import com.lambda.interaction.managers.rotating.RotationMode

class RotationSettings(override val c: Config) : RotationConfig, ConfigBlock {
    override var rotationMode by c.setting("Mode", RotationMode.Sync, "How the player is being rotated on interaction")

    /** How many ticks to keep the rotation before resetting */
    override val keepTicks by c.setting("Keep Rotation", 1, 1..10, 1, "Ticks to keep rotation", " ticks")

    /** How many ticks to wait before resetting the rotation */
    override val decayTicks by c.setting("Reset Rotation", 1, 1..10, 1, "Ticks before rotation is reset", " ticks")

    override val tickStageMask = TickEvent.ROTATION_STAGES.toSet()

    /** Whether the rotation is instant */
    var instant by c.setting("Instant Rotation", true, "Instantly rotate")

    var mean by c.setting("Mean", 40.0, 1.0..120.0, 0.1, "Average rotation speed", unit = "°") { !instant }

    var spread by c.setting("Spread", 10.0, 0.0..60.0, 0.1, "Spread of rotation speeds", unit = "°") { !instant }

    var acceleration by c.setting("Acceleration", 90.0, 1.0..180.0, 0.5, "How much the rotation speed may change from one tick to the next", unit = "°") { !instant }

    override val turnSpeed get() = if (instant) 180.0 else mean

    override val turnAcceleration get() = if (instant) 360.0 else acceleration

    override val turnNoise get() = if (instant) 0.0 else spread
}
