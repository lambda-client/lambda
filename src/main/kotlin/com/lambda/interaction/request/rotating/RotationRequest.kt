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

package com.lambda.interaction.request.rotating

import com.lambda.context.Automated
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.rotating.visibilty.RotationTarget
import com.lambda.threading.runSafe

data class RotationRequest(
    val target: RotationTarget,
    private val automated: Automated,
    val rotationMode: RotationMode = automated.rotationConfig.rotationMode,
    val turnSpeed: Double = automated.rotationConfig.turnSpeed,
    var keepTicks: Int = automated.rotationConfig.keepTicks,
    var decayTicks: Int = automated.rotationConfig.decayTicks,
    val speedMultiplier: Double = 1.0
) : Request(), LogContext, Automated by automated {
    override val requestID = ++requestCount

    var age = 0

    override val done: Boolean get() =
        rotationMode == RotationMode.None || runSafe { target.verify() } == true

    override fun submit(queueIfClosed: Boolean): RotationRequest =
        RotationManager.request(this, queueIfClosed)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Rotation Request") {
            value("Request ID", requestID)
            value("Rotation Mode", rotationMode)
            value("Turn Speed", turnSpeed)
            value("Keep Ticks", keepTicks)
            value("Decay Ticks", decayTicks)
            value("Speed Multiplier", speedMultiplier)
            value("Age", age)
        }
    }

    companion object {
        var requestCount = 0
    }
}