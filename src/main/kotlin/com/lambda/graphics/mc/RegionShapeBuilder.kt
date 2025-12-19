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

package com.lambda.graphics.mc

import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.outlineShape
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

/**
 * Shape builder for region-based rendering. All coordinates are automatically converted to
 * region-relative positions.
 *
 * This class provides the same DSL as ShapeDsl but collects vertex data in thread-safe collections
 * for later upload to MC's BufferBuilder.
 *
 * @param region The render region (provides origin for coordinate conversion)
 */
class RegionShapeBuilder(val region: RenderRegion) {
	val collector = RegionVertexCollector()

	private val lineWidth: Float
		get() = StyleEditor.outlineWidth.toFloat()

	/** Convert world coordinates to region-relative. */
	private fun toRelative(x: Double, y: Double, z: Double) = Triple(
		(x - region.originX).toFloat(),
		(y - region.originY).toFloat(),
		(z - region.originZ).toFloat()
	)

	/** Add a colored quad face (filled rectangle). */
	fun filled(
		box: Box,
		bottomColor: Color,
		topColor: Color = bottomColor,
		sides: Int = DirectionMask.ALL
	) {
		val (x1, y1, z1) = toRelative(box.minX, box.minY, box.minZ)
		val (x2, y2, z2) = toRelative(box.maxX, box.maxY, box.maxZ)

		// Bottom-left-back, bottom-left-front, etc.
		if (sides.hasDirection(DirectionMask.EAST)) {
			// East face (+X)
			faceVertex(x2, y1, z1, bottomColor)
			faceVertex(x2, y2, z1, topColor)
			faceVertex(x2, y2, z2, topColor)
			faceVertex(x2, y1, z2, bottomColor)
		}
		if (sides.hasDirection(DirectionMask.WEST)) {
			// West face (-X)
			faceVertex(x1, y1, z1, bottomColor)
			faceVertex(x1, y1, z2, bottomColor)
			faceVertex(x1, y2, z2, topColor)
			faceVertex(x1, y2, z1, topColor)
		}
		if (sides.hasDirection(DirectionMask.UP)) {
			// Top face (+Y)
			faceVertex(x1, y2, z1, topColor)
			faceVertex(x1, y2, z2, topColor)
			faceVertex(x2, y2, z2, topColor)
			faceVertex(x2, y2, z1, topColor)
		}
		if (sides.hasDirection(DirectionMask.DOWN)) {
			// Bottom face (-Y)
			faceVertex(x1, y1, z1, bottomColor)
			faceVertex(x2, y1, z1, bottomColor)
			faceVertex(x2, y1, z2, bottomColor)
			faceVertex(x1, y1, z2, bottomColor)
		}
		if (sides.hasDirection(DirectionMask.SOUTH)) {
			// South face (+Z)
			faceVertex(x1, y1, z2, bottomColor)
			faceVertex(x2, y1, z2, bottomColor)
			faceVertex(x2, y2, z2, topColor)
			faceVertex(x1, y2, z2, topColor)
		}
		if (sides.hasDirection(DirectionMask.NORTH)) {
			// North face (-Z)
			faceVertex(x1, y1, z1, bottomColor)
			faceVertex(x1, y2, z1, topColor)
			faceVertex(x2, y2, z1, topColor)
			faceVertex(x2, y1, z1, bottomColor)
		}
	}

	fun filled(box: Box, color: Color, sides: Int = DirectionMask.ALL) =
		filled(box, color, color, sides)

	fun filled(box: DynamicAABB, color: Color, sides: Int = DirectionMask.ALL) {
		box.pair?.second?.let { filled(it, color, sides) }
	}

	fun filled(pos: BlockPos, state: BlockState, color: Color, sides: Int = DirectionMask.ALL) =
		runSafe {
			val shape = outlineShape(state, pos)
			if (shape.isEmpty) {
				filled(Box(pos), color, sides)
			} else {
				filled(shape, color, sides)
			}
		}

	fun filled(pos: BlockPos, color: Color, sides: Int = DirectionMask.ALL) = runSafe {
		filled(pos, blockState(pos), color, sides)
	}

	fun filled(pos: BlockPos, entity: BlockEntity, color: Color, sides: Int = DirectionMask.ALL) =
		filled(pos, entity.cachedState, color, sides)

	fun filled(shape: VoxelShape, color: Color, sides: Int = DirectionMask.ALL) {
		shape.boundingBoxes.forEach { filled(it, color, color, sides) }
	}

	/** Add outline (lines) for a box. */
	fun outline(
		box: Box,
		bottomColor: Color,
		topColor: Color = bottomColor,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		val (x1, y1, z1) = toRelative(box.minX, box.minY, box.minZ)
		val (x2, y2, z2) = toRelative(box.maxX, box.maxY, box.maxZ)

		val hasEast = sides.hasDirection(DirectionMask.EAST)
		val hasWest = sides.hasDirection(DirectionMask.WEST)
		val hasUp = sides.hasDirection(DirectionMask.UP)
		val hasDown = sides.hasDirection(DirectionMask.DOWN)
		val hasSouth = sides.hasDirection(DirectionMask.SOUTH)
		val hasNorth = sides.hasDirection(DirectionMask.NORTH)

		// Top edges
		if (mode.check(hasUp, hasNorth)) line(x1, y2, z1, x2, y2, z1, topColor)
		if (mode.check(hasUp, hasSouth)) line(x1, y2, z2, x2, y2, z2, topColor)
		if (mode.check(hasUp, hasWest)) line(x1, y2, z1, x1, y2, z2, topColor)
		if (mode.check(hasUp, hasEast)) line(x2, y2, z2, x2, y2, z1, topColor)

		// Bottom edges
		if (mode.check(hasDown, hasNorth)) line(x1, y1, z1, x2, y1, z1, bottomColor)
		if (mode.check(hasDown, hasSouth)) line(x1, y1, z2, x2, y1, z2, bottomColor)
		if (mode.check(hasDown, hasWest)) line(x1, y1, z1, x1, y1, z2, bottomColor)
		if (mode.check(hasDown, hasEast)) line(x2, y1, z1, x2, y1, z2, bottomColor)

		// Vertical edges
		if (mode.check(hasWest, hasNorth)) line(x1, y2, z1, x1, y1, z1, topColor, bottomColor)
		if (mode.check(hasNorth, hasEast)) line(x2, y2, z1, x2, y1, z1, topColor, bottomColor)
		if (mode.check(hasEast, hasSouth)) line(x2, y2, z2, x2, y1, z2, topColor, bottomColor)
		if (mode.check(hasSouth, hasWest)) line(x1, y2, z2, x1, y1, z2, topColor, bottomColor)
	}

	fun outline(
		box: Box,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = outline(box, color, color, sides, mode)

	fun outline(
		box: DynamicAABB,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		box.pair?.second?.let { outline(it, color, sides, mode) }
	}

	fun outline(
		pos: BlockPos,
		state: BlockState,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		val shape = outlineShape(state, pos)
		if (shape.isEmpty) {
			outline(Box(pos), color, sides, mode)
		} else {
			outline(shape, color, sides, mode)
		}
	}

	fun outline(
		pos: BlockPos,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe { outline(pos, blockState(pos), color, sides, mode) }

	fun outline(
		pos: BlockPos,
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe { outline(pos, entity.cachedState, color, sides, mode) }

	fun outline(
		shape: VoxelShape,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		shape.boundingBoxes.forEach { outline(it, color, sides, mode) }
	}

	/** Add both filled and outline for a box. */
	fun box(
		pos: BlockPos,
		state: BlockState,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		filled(pos, state, filledColor, sides)
		outline(pos, state, outlineColor, sides, mode)
	}

	fun box(
		pos: BlockPos,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		filled(pos, filledColor, sides)
		outline(pos, outlineColor, sides, mode)
	}

	fun box(
		box: Box,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		filled(box, filledColor, sides)
		outline(box, outlineColor, sides, mode)
	}

	fun box(
		box: DynamicAABB,
		filledColor: Color,
		outlineColor: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		filled(box, filledColor, sides)
		outline(box, outlineColor, sides, mode)
	}

	fun box(
		entity: BlockEntity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		filled(entity.pos, entity, color, sides)
		outline(entity.pos, entity, color, sides, mode)
	}

	fun box(
		entity: Entity,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		filled(entity.boundingBox, color, sides)
		outline(entity.boundingBox, color, sides, mode)
	}

	// =========== Private helpers ===========

	private fun faceVertex(x: Float, y: Float, z: Float, color: Color) {
		collector.addFaceVertex(x, y, z, color)
	}

	private fun line(
		x1: Float,
		y1: Float,
		z1: Float,
		x2: Float,
		y2: Float,
		z2: Float,
		color: Color
	) {
		line(x1, y1, z1, x2, y2, z2, color, color)
	}

	private fun line(
		x1: Float,
		y1: Float,
		z1: Float,
		x2: Float,
		y2: Float,
		z2: Float,
		color1: Color,
		color2: Color
	) {
		// Calculate normal (direction of line)
		val dx = x2 - x1
		val dy = y2 - y1
		val dz = z2 - z1
		val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
		val nx = if (len > 0) dx / len else 0f
		val ny = if (len > 0) dy / len else 1f
		val nz = if (len > 0) dz / len else 0f

		collector.addEdgeVertex(x1, y1, z1, color1, nx, ny, nz, lineWidth)
		collector.addEdgeVertex(x2, y2, z2, color2, nx, ny, nz, lineWidth)
	}
}
