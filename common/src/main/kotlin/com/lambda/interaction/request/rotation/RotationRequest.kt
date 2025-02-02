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
import com.lambda.interaction.request.rotation.visibilty.RotationTarget
import com.lambda.threading.runSafe

class RotationRequest(
    val target: RotationTarget,
    priority: Priority,
    val mode: RotationMode,
    var keepTicks: Int = 3,
    var decayTicks: Int = 0,
    val turnSpeed: () -> Double = { 180.0 },
    val speedMultiplier: Double = 1.0
) : Request(priority) {

    constructor(
        target: RotationTarget,
        priority: Priority,
        mode: RotationMode,
        keepTicks: Int = 3,
        decayTicks: Int = 0,
        turnSpeed: Double = 180.0,
        speedMultiplier: Double = 1.0
    ) : this(target, priority, mode, keepTicks, decayTicks, { turnSpeed }, speedMultiplier)

    constructor(
        target: RotationTarget,
        config: RotationConfig,
        speedMultiplier: Double = 1.0
    ) : this(target, config.priority, config.rotationMode, config.keepTicks, config.decayTicks, config::turnSpeed, speedMultiplier)

    override val done: Boolean get() =
        mode == RotationMode.NONE || runSafe {
            target.verify(target)
        } == true
}
