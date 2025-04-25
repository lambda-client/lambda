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

package com.lambda.interaction.request.rotation.visibilty

import com.lambda.interaction.request.rotation.Rotation.Companion.dist
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.util.math.distSq
import com.lambda.util.math.times

enum class PointSelection(val select: (MutableList<VisibilityChecker.CheckedHit>) -> VisibilityChecker.CheckedHit?) {
    ByRotation({ hits ->
        hits.minByOrNull {
            RotationManager.activeRotation dist it.targetRotation
        }
    }),
    Optimum({ hits ->
        val optimum = hits
            .map { it.hit.pos }
            .reduceOrNull { acc, pos -> acc.add(pos) }
            ?.times(1 / hits.size)

        optimum?.let {
            hits.minByOrNull { it.hit.pos distSq optimum }
        }
    })
}
