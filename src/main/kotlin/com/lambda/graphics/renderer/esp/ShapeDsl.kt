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
	) = esp.getBuilder(box.minX, box.minY, box.minZ).filled(box, bottomColor, topColor, sides)

	@ShapeDsl
	fun filled(pos: BlockPos, state: BlockState, color: Color, sides: Int = DirectionMask.ALL) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.filled(pos, state, color, sides)

	@ShapeDsl
	fun filled(pos: BlockPos, color: Color, sides: Int = DirectionMask.ALL) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.filled(pos, color, sides)

	@ShapeDsl
	fun filled(
		pos: BlockPos,
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL
	) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.filled(pos, entity, color, sides)

	@ShapeDsl
	fun filled(shape: VoxelShape, color: Color, sides: Int = DirectionMask.ALL) =
		esp.getBuilder(
			shape.boundingBoxes[0].minX,
			shape.boundingBoxes[0].minY,
			shape.boundingBoxes[0].minZ
		)
			.filled(shape, color, sides)

	@ShapeDsl
	fun filled(box: Box, color: Color, sides: Int = DirectionMask.ALL) =
		filled(box, color, color, sides)

	@ShapeDsl
	fun outline(
		box: Box,
		bottomColor: Color,
		topColor: Color = bottomColor,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) =
		esp.getBuilder(box.minX, box.minY, box.minZ)
			.outline(box, bottomColor, topColor, sides, mode)

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		state: BlockState,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.outline(pos, state, color, sides, mode)

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.outline(pos, color, sides, mode)

	@ShapeDsl
	fun outline(
		pos: BlockPos,
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) =
		esp.getBuilder(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
			.outline(pos, entity, color, sides, mode)

	@ShapeDsl
	fun outline(
		shape: VoxelShape,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
	) =
		esp.getBuilder(
			shape.boundingBoxes[0].minX,
			shape.boundingBoxes[0].minY,
			shape.boundingBoxes[0].minZ
		)
			.outline(shape, color, sides, mode)

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
		box.pair?.second?.let {
			esp.getBuilder(it.minX, it.minY, it.minZ).filled(box, color, sides)
		}
	}

	@ShapeDsl
	fun outline(
		box: DynamicAABB,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		box.pair?.second?.let {
			esp.getBuilder(it.minX, it.minY, it.minZ).outline(box, color, sides, mode)
		}
	}

	@ShapeDsl
	fun box(
		box: DynamicAABB,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		box.pair?.second?.let {
			esp.getBuilder(it.minX, it.minY, it.minZ)
				.box(box, filledColor, outlineColor, sides, mode)
		}
	}
}
