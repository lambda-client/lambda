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

package com.lambda.interaction.request.rotating.visibilty

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.dist
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.threading.runSafe
import com.lambda.util.collections.updatableLazy

/**
 * Represents a target for rotation.
 *
 * @param hit The requested hit to look at.
 * @param verify A lambda to check the active rotation.
 * @param buildRotation A lambda that builds the rotation.
 */
data class RotationTarget(
    val hit: RequestedHit? = null,
    val verify: () -> Boolean = { hit?.verifyRotation() ?: true },
    private val buildRotation: SafeContext.() -> Rotation?,
) {
    val targetRotation = updatableLazy {
        runSafe { buildRotation() }
    }

    val angleDistance get() = runSafe {
        targetRotation.value?.dist(RotationManager.serverRotation)
    } ?: 1000.0

    /**
     * Requests a rotation based on the given configuration.
     *
     * @param config The rotation configuration.
     * @return [RotationRequest] containing this [RotationTarget].
     */
    fun requestBy(automated: Automated, queueIfClosed: Boolean = true) =
        RotationRequest(this, automated).submit(queueIfClosed)
}
