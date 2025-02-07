/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request.rotation

import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.rotation.Rotation.Companion.dist
import com.lambda.interaction.request.rotation.visibilty.RotationTarget
import com.lambda.threading.runSafe

data class RotationRequest(
    val target: RotationTarget,
    val prio: Priority,
    val mode: RotationMode,
    var keepTicks: Int = 3,
    var decayTicks: Int = 0,
    val turnSpeed: () -> Double = { 180.0 },
    val speedMultiplier: Double = 1.0
) : Request(prio) {

    constructor(
        target: RotationTarget,
        config: RotationConfig,
        speedMultiplier: Double = 1.0
    ) : this(target, config.priority, config.rotationMode, config.keepTicks, config.decayTicks, config::turnSpeed, speedMultiplier)

    override val done: Boolean get() =
        mode == RotationMode.None || runSafe {
            target.verify(target)
        } == true
}
