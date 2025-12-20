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

package com.lambda.interaction.managers.rotating

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.LogContext
import com.lambda.interaction.managers.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.managers.Request
import com.lambda.interaction.managers.rotating.Rotation.Companion.dist
import com.lambda.threading.runSafe
import com.lambda.util.collections.updatableLazy

data class RotationRequest(
    val buildRotation: SafeContext.() -> Rotation?,
    private val automated: Automated,
    var keepTicks: Int = automated.rotationConfig.keepTicks,
    var decayTicks: Int = automated.rotationConfig.decayTicks
) : Request(), LogContext, Automated by automated {
    constructor(
        rotation: Rotation,
        automated: Automated,
        keepTicks: Int = automated.rotationConfig.keepTicks,
        decayTicks: Int = automated.rotationConfig.decayTicks
    ) : this({ rotation }, automated, keepTicks, decayTicks)

    override val requestId = ++requestCount
    override val tickStageMask get() = rotationConfig.tickStageMask

    val rotation = updatableLazy {
        runSafe { buildRotation() }
    }

    var age = 0
    override val nowOrNothing = false

    override val done: Boolean get() = RotationManager.activeRotation.dist(rotation.value ?: return false) <= 0.001

    override fun submit(queueIfMismatchedStage: Boolean): RotationRequest =
        RotationManager.request(this, queueIfMismatchedStage)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Rotation Request") {
            value("Request ID", requestId)
            value("Age", age)
            value("Keep Ticks", keepTicks)
            value("Decay Ticks", decayTicks)
        }
    }

    companion object {
        var requestCount = 0
    }
}