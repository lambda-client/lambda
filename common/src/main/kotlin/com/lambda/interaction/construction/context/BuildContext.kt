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

package com.lambda.interaction.construction.context

import com.lambda.Lambda.mc
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.request.rotating.RotationRequest
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos

abstract class BuildContext : Comparable<BuildContext>, Drawable {
    abstract val result: BlockHitResult
    abstract val rotation: RotationRequest
    abstract val hotbarIndex: Int
    abstract val cachedState: BlockState
    abstract val expectedState: BlockState
    abstract val blockPos: BlockPos

    val distance by lazy {
        mc.player?.eyePos?.distanceTo(result.pos) ?: Double.MAX_VALUE
    }
}
