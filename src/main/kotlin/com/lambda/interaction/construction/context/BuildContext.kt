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

package com.lambda.interaction.construction.context

import com.lambda.config.groups.BuildConfig
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.rotation.RotationRequest
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

interface BuildContext : Comparable<BuildContext>, Drawable {
    val pov: Vec3d
    val result: BlockHitResult
    val distance: Double
    val expectedState: BlockState
    val targetState: TargetState
    val expectedPos: BlockPos
    val checkedState: BlockState
    val hand: Hand
    val rotation: RotationRequest

    fun interact(swingHand: Boolean)
    fun shouldRotate(config: BuildConfig): Boolean
}
