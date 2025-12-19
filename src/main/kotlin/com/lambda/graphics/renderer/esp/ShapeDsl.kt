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

package com.lambda.graphics.renderer.esp

import com.lambda.graphics.mc.TransientRegionESP
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

@DslMarker
annotation class ShapeDsl

/**
 * Bridge class that provides the legacy ShapeBuilder API while writing to the new
 * TransientRegionESP.
 */
class ShapeBuilder(val esp: TransientRegionESP) {
	@ShapeDsl
	fun filled(
		box: Box,
		bottomColor: Color,
		topColor: Color = bottomColor,
		sides: Int = DirectionMask.ALL
	) = Unit

	@ShapeDsl
	fun filled(pos: BlockPos, state: BlockState, color: Color, sides: Int = DirectionMask.ALL) = Unit

	@ShapeDsl
	fun filled(pos: BlockPos, color: Color, sides: Int = DirectionMask.ALL) =
		Unit

	@ShapeDsl
	fun filled(
		pos: BlockPos,
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL
	) = Unit

	@ShapeDsl
	fun filled(shape: VoxelShape, color: Color, sides: Int = DirectionMask.ALL) = Unit

	@ShapeDsl
	fun filled(box: Box, color: Color, sides: Int = DirectionMask.ALL) = Unit

	@ShapeDsl
	fun outline(
		box: Box,
		bottomColor: Color,
		topColor: Color = bottomColor,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = Unit

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		state: BlockState,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = Unit

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = Unit

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = Unit

	@ShapeDsl
	fun outline(
		shape: VoxelShape,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = Unit

	@ShapeDsl
	fun outline(
		box: Box,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) = outline(box, color, color, sides, mode)

	@ShapeDsl
	fun box(
		pos: BlockPos,
		state: BlockState,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) {
		filled(pos, state, filled, sides)
		outline(pos, state, outline, sides, mode)
	}

	@ShapeDsl
	fun box(
		pos: BlockPos,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) {
		filled(pos, filled, sides)
		outline(pos, outline, sides, mode)
	}

	@ShapeDsl
	fun box(
		box: Box,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) {
		filled(box, filled, sides)
		outline(box, outline, sides, mode)
	}

	@ShapeDsl
	fun box(
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) {
		filled(entity.pos, entity, color, sides)
		outline(entity.pos, entity, color, sides, mode)
	}

	@ShapeDsl
	fun box(
		entity: Entity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) {
		filled(entity.boundingBox, color, sides)
		outline(entity.boundingBox, color, sides, mode)
	}

	@ShapeDsl
	fun filled(box: DynamicAABB, color: Color, sides: Int = DirectionMask.ALL) {
		Unit
	}

	@ShapeDsl
	fun outline(
		box: DynamicAABB,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		Unit
	}

	@ShapeDsl
	fun box(
		box: DynamicAABB,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		Unit
	}
}
