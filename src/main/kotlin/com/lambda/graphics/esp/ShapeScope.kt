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

package com.lambda.graphics.esp

import com.lambda.graphics.mc.RegionShapeBuilder
import com.lambda.graphics.mc.RenderRegion
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DynamicAABB
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

@EspDsl
class ShapeScope(val region: RenderRegion) {
	internal val builder = RegionShapeBuilder(region)

	/** Start building a box. */
	fun box(box: Box, block: BoxScope.() -> Unit) {
		val scope = BoxScope(box, this)
		scope.apply(block)
	}

	/** Draw a line between two points. */
	fun line(start: Vec3d, end: Vec3d, color: Color, width: Float = 1.0f) {
		builder.line(start, end, color, width)
	}

	/** Draw a tracer. */
	fun line(from: Vec3d, to: Vec3d, block: LineScope.() -> Unit = {}) {
		val scope = LineScope(from, to, this)
		scope.apply(block)
		scope.draw()
	}

	/** Draw a simple filled box. */
	fun filled(box: Box, color: Color, sides: Int = DirectionMask.ALL) {
		builder.filled(box, color, sides)
	}

	/** Draw a simple outlined box. */
	fun outline(box: Box, color: Color, sides: Int = DirectionMask.ALL, thickness: Float = builder.lineWidth) {
		builder.outline(box, color, sides, thickness = thickness)
	}

	fun filled(box: DynamicAABB, color: Color, sides: Int = DirectionMask.ALL) {
		builder.filled(box, color, sides)
	}

	fun outline(box: DynamicAABB, color: Color, sides: Int = DirectionMask.ALL, thickness: Float = builder.lineWidth) {
		builder.outline(box, color, sides, thickness = thickness)
	}

	fun filled(pos: BlockPos, color: Color, sides: Int = DirectionMask.ALL) {
		builder.filled(pos, color, sides)
	}

	fun outline(pos: BlockPos, color: Color, sides: Int = DirectionMask.ALL, thickness: Float = builder.lineWidth) {
		builder.outline(pos, color, sides, thickness = thickness)
	}

	fun filled(pos: BlockPos, state: BlockState, color: Color, sides: Int = DirectionMask.ALL) {
		builder.filled(pos, state, color, sides)
	}

	fun outline(pos: BlockPos, state: BlockState, color: Color, sides: Int = DirectionMask.ALL, thickness: Float = builder.lineWidth) {
		builder.outline(pos, state, color, sides, thickness = thickness)
	}

	fun filled(shape: VoxelShape, color: Color, sides: Int = DirectionMask.ALL) {
		builder.filled(shape, color, sides)
	}

	fun outline(shape: VoxelShape, color: Color, sides: Int = DirectionMask.ALL, thickness: Float = builder.lineWidth) {
		builder.outline(shape, color, sides, thickness = thickness)
	}

	fun box(
		pos: BlockPos,
		state: BlockState,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(pos, state, filled, outline, sides, mode, thickness = thickness)
	}

	fun box(
		pos: BlockPos,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(pos, filled, outline, sides, mode, thickness = thickness)
	}

	fun box(
		box: Box,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(box, filledColor, outlineColor, sides, mode, thickness = thickness)
	}

	fun box(
		box: DynamicAABB,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(box, filledColor, outlineColor, sides, mode, thickness = thickness)
	}

	fun box(
		entity: net.minecraft.block.entity.BlockEntity,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(entity, filled, outline, sides, mode, thickness = thickness)
	}

	fun box(
		entity: net.minecraft.entity.Entity,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = builder.lineWidth
	) {
		builder.box(entity, filled, outline, sides, mode, thickness = thickness)
	}
}

@EspDsl
class BoxScope(val box: Box, val parent: ShapeScope) {
	internal var filledColor: Color? = null
	internal var outlineColor: Color? = null
	internal var sides: Int = DirectionMask.ALL
	internal var outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	internal var thickness: Float = parent.builder.lineWidth

	fun filled(color: Color, sides: Int = DirectionMask.ALL) {
		this.filledColor = color
		this.sides = sides
		parent.builder.filled(box, color, sides)
	}

	fun outline(
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And,
		thickness: Float = parent.builder.lineWidth
	) {
		this.outlineColor = color
		this.sides = sides
		this.outlineMode = mode
		this.thickness = thickness
		parent.builder.outline(box, color, sides, mode, thickness = thickness)
	}
}

@EspDsl
class LineScope(val from: Vec3d, val to: Vec3d, val parent: ShapeScope) {
	internal var lineColor: Color = Color.WHITE
	internal var lineWidth: Float = 1.0f
	internal var lineDashLength: Double? = null
	internal var lineGapLength: Double? = null

	fun color(color: Color) {
		this.lineColor = color
	}

	fun width(width: Float) {
		this.lineWidth = width
	}

	fun dashed(dashLength: Double = 0.5, gapLength: Double = 0.25) {
		this.lineDashLength = dashLength
		this.lineGapLength = gapLength
	}

	internal fun draw() {
		val dLen = lineDashLength
		val gLen = lineGapLength

		if (dLen != null && gLen != null) {
			parent.builder.dashedLine(from, to, lineColor, dLen, gLen, lineWidth)
		} else {
			parent.builder.line(from, to, lineColor, lineWidth)
		}
	}
}
