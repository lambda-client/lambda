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

package com.lambda.interaction.rotation

import com.lambda.config.groups.RotationConfig
import com.lambda.interaction.RotationManager
import com.lambda.threading.runSafe
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import net.minecraft.util.hit.HitResult

data class RotationRequest(
    val rotation: Rotation,
    val config: RotationConfig,
    val checkedResult: HitResult? = null,
    val verify: HitResult.() -> Boolean = { true },
) {
    val cast: HitResult? get() = runSafe { RotationManager.currentRotation.rayCast(10.0, player.eyePos) }
    val isValid: Boolean get() = runSafe {
        // ToDo: Use proper reach
        val result = cast
        verify(result.orMiss)
    } ?: false
}
