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

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.partialTicks
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper.lerp
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import java.awt.Color
import kotlin.math.min
import kotlin.math.sqrt

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

	fun box(
		entity: BlockEntity,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = box(entity.pos, entity.cachedState, filled, outline, sides, mode)

	fun box(
		entity: Entity,
		filled: Color,
		outline: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = box(entity.boundingBox, filled, outline, sides, mode)

	/** Convert world coordinates to region-relative. */
	private fun toRelative(x: Double, y: Double, z: Double) =
		Triple(
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
		val pair = box.pair ?: return
		val prev = pair.first
		val curr = pair.second
		val tickDelta = mc.partialTicks
		val interpolated = Box(
			lerp(tickDelta, prev.minX, curr.minX),
			lerp(tickDelta, prev.minY, curr.minY),
			lerp(tickDelta, prev.minZ, curr.minZ),
			lerp(tickDelta, prev.maxX, curr.maxX),
			lerp(tickDelta, prev.maxY, curr.maxY),
			lerp(tickDelta, prev.maxZ, curr.maxZ)
		)
		filled(interpolated, color, sides)
	}

	fun filled(
		pos: BlockPos,
		state: BlockState,
		color: Color,
		sides: Int = DirectionMask.ALL
	) = runSafe {
		val shape = state.getOutlineShape(world, pos)
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
		val pair = box.pair ?: return
		val prev = pair.first
		val curr = pair.second
		val tickDelta = mc.partialTicks
		val interpolated = Box(
			lerp(tickDelta, prev.minX, curr.minX),
			lerp(tickDelta, prev.minY, curr.minY),
			lerp(tickDelta, prev.minZ, curr.minZ),
			lerp(
				tickDelta,
				prev.maxX,
				curr.maxX
			),
			lerp(
				mc.partialTicks.toDouble(),
				prev.maxY,
				curr.maxY
			),
			lerp(
				mc.partialTicks.toDouble(),
				prev.maxZ,
				curr.maxZ
			)
		)
		outline(interpolated, color, sides, mode)
	}

	fun outline(
		pos: BlockPos,
		state: BlockState,
		color: Color,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) = runSafe {
		val shape = state.getOutlineShape(world, pos)
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
		val len = sqrt(dx * dx + dy * dy + dz * dz)
		val nx = if (len > 0) dx / len else 0f
		val ny = if (len > 0) dy / len else 1f
		val nz = if (len > 0) dz / len else 0f

		collector.addEdgeVertex(x1, y1, z1, color1, nx, ny, nz, lineWidth)
		collector.addEdgeVertex(x2, y2, z2, color2, nx, ny, nz, lineWidth)
	}

	/**
	 * Draw a dashed line between two world positions.
	 *
	 * @param start Start position in world coordinates
	 * @param end End position in world coordinates
	 * @param color Line color
	 * @param dashLength Length of each dash in blocks
	 * @param gapLength Length of each gap in blocks
	 * @param width Line width (uses default if null)
	 */
	fun dashedLine(
		start: Vec3d,
		end: Vec3d,
		color: Color,
		dashLength: Double = 0.5,
		gapLength: Double = 0.25,
		width: Float = lineWidth
	) {
		val direction = end.subtract(start)
		val totalLength = direction.length()
		if (totalLength < 0.001) return

		val normalizedDir = direction.normalize()
		var pos = 0.0
		var isDash = true

		while (pos < totalLength) {
			val segmentLength = if (isDash) dashLength else gapLength
			val segmentEnd = min(pos + segmentLength, totalLength)

			if (isDash) {
				val segStart = start.add(normalizedDir.multiply(pos))
				val segEnd = start.add(normalizedDir.multiply(segmentEnd))

				val (x1, y1, z1) = toRelative(segStart.x, segStart.y, segStart.z)
				val (x2, y2, z2) = toRelative(segEnd.x, segEnd.y, segEnd.z)

				lineWithWidth(x1, y1, z1, x2, y2, z2, color, width)
			}

			pos = segmentEnd
			isDash = !isDash
		}
	}

	/** Draw a dashed outline for a box. */
	fun dashedOutline(
		box: Box,
		color: Color,
		dashLength: Double = 0.5,
		gapLength: Double = 0.25,
		sides: Int = DirectionMask.ALL,
		mode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
	) {
		val hasEast = sides.hasDirection(DirectionMask.EAST)
		val hasWest = sides.hasDirection(DirectionMask.WEST)
		val hasUp = sides.hasDirection(DirectionMask.UP)
		val hasDown = sides.hasDirection(DirectionMask.DOWN)
		val hasSouth = sides.hasDirection(DirectionMask.SOUTH)
		val hasNorth = sides.hasDirection(DirectionMask.NORTH)

		// Top edges
		if (mode.check(hasUp, hasNorth))
			dashedLine(
				Vec3d(box.minX, box.maxY, box.minZ),
				Vec3d(box.maxX, box.maxY, box.minZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasUp, hasSouth))
			dashedLine(
				Vec3d(box.minX, box.maxY, box.maxZ),
				Vec3d(box.maxX, box.maxY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasUp, hasWest))
			dashedLine(
				Vec3d(box.minX, box.maxY, box.minZ),
				Vec3d(box.minX, box.maxY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasUp, hasEast))
			dashedLine(
				Vec3d(box.maxX, box.maxY, box.maxZ),
				Vec3d(box.maxX, box.maxY, box.minZ),
				color,
				dashLength,
				gapLength
			)

		// Bottom edges
		if (mode.check(hasDown, hasNorth))
			dashedLine(
				Vec3d(box.minX, box.minY, box.minZ),
				Vec3d(box.maxX, box.minY, box.minZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasDown, hasSouth))
			dashedLine(
				Vec3d(box.minX, box.minY, box.maxZ),
				Vec3d(box.maxX, box.minY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasDown, hasWest))
			dashedLine(
				Vec3d(box.minX, box.minY, box.minZ),
				Vec3d(box.minX, box.minY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasDown, hasEast))
			dashedLine(
				Vec3d(box.maxX, box.minY, box.minZ),
				Vec3d(box.maxX, box.minY, box.maxZ),
				color,
				dashLength,
				gapLength
			)

		// Vertical edges
		if (mode.check(hasWest, hasNorth))
			dashedLine(
				Vec3d(box.minX, box.maxY, box.minZ),
				Vec3d(box.minX, box.minY, box.minZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasNorth, hasEast))
			dashedLine(
				Vec3d(box.maxX, box.maxY, box.minZ),
				Vec3d(box.maxX, box.minY, box.minZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasEast, hasSouth))
			dashedLine(
				Vec3d(box.maxX, box.maxY, box.maxZ),
				Vec3d(box.maxX, box.minY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
		if (mode.check(hasSouth, hasWest))
			dashedLine(
				Vec3d(box.minX, box.maxY, box.maxZ),
				Vec3d(box.minX, box.minY, box.maxZ),
				color,
				dashLength,
				gapLength
			)
	}

	/** Draw a line between two world positions. */
	fun line(start: Vec3d, end: Vec3d, color: Color, width: Float = lineWidth) {
		val (x1, y1, z1) = toRelative(start.x, start.y, start.z)
		val (x2, y2, z2) = toRelative(end.x, end.y, end.z)
		lineWithWidth(x1, y1, z1, x2, y2, z2, color, width)
	}

	/** Draw a polyline through a list of points. */
	fun polyline(points: List<Vec3d>, color: Color, width: Float = lineWidth) {
		if (points.size < 2) return
		for (i in 0 until points.size - 1) {
			line(points[i], points[i + 1], color, width)
		}
	}

	/** Draw a dashed polyline through a list of points. */
	fun dashedPolyline(
		points: List<Vec3d>,
		color: Color,
		dashLength: Double = 0.5,
		gapLength: Double = 0.25,
		width: Float = lineWidth
	) {
		if (points.size < 2) return
		for (i in 0 until points.size - 1) {
			dashedLine(points[i], points[i + 1], color, dashLength, gapLength, width)
		}
	}

	/**
	 * Draw a quadratic Bezier curve.
	 *
	 * @param p0 Start point
	 * @param p1 Control point
	 * @param p2 End point
	 * @param color Line color
	 * @param segments Number of line segments (higher = smoother)
	 */
	fun quadraticBezier(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		color: Color,
		segments: Int = 16,
		width: Float = lineWidth
	) {
		val points = CurveUtils.quadraticBezierPoints(p0, p1, p2, segments)
		polyline(points, color, width)
	}

	/**
	 * Draw a cubic Bezier curve.
	 *
	 * @param p0 Start point
	 * @param p1 First control point
	 * @param p2 Second control point
	 * @param p3 End point
	 * @param color Line color
	 * @param segments Number of line segments (higher = smoother)
	 */
	fun cubicBezier(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		p3: Vec3d,
		color: Color,
		segments: Int = 32,
		width: Float = lineWidth
	) {
		val points = CurveUtils.cubicBezierPoints(p0, p1, p2, p3, segments)
		polyline(points, color, width)
	}

	/**
	 * Draw a Catmull-Rom spline that passes through all control points.
	 *
	 * @param controlPoints List of points the spline should pass through (minimum 4)
	 * @param color Line color
	 * @param segmentsPerSection Segments between each pair of control points
	 */
	fun catmullRomSpline(
		controlPoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float = lineWidth
	) {
		val points = CurveUtils.catmullRomSplinePoints(controlPoints, segmentsPerSection)
		polyline(points, color, width)
	}

	/**
	 * Draw a smooth path through waypoints using Catmull-Rom splines. Handles endpoints
	 * naturally by mirroring.
	 *
	 * @param waypoints List of points to pass through (minimum 2)
	 * @param color Line color
	 * @param segmentsPerSection Smoothness (higher = smoother)
	 */
	fun smoothPath(
		waypoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float = lineWidth
	) {
		val points = CurveUtils.smoothPath(waypoints, segmentsPerSection)
		polyline(points, color, width)
	}

	/** Draw a dashed Bezier curve. */
	fun dashedCubicBezier(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		p3: Vec3d,
		color: Color,
		segments: Int = 32,
		dashLength: Double = 0.5,
		gapLength: Double = 0.25,
		width: Float = lineWidth
	) {
		val points = CurveUtils.cubicBezierPoints(p0, p1, p2, p3, segments)
		dashedPolyline(points, color, dashLength, gapLength, width)
	}

	/** Draw a dashed smooth path. */
	fun dashedSmoothPath(
		waypoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		dashLength: Double = 0.5,
		gapLength: Double = 0.25,
		width: Float = lineWidth
	) {
		val points = CurveUtils.smoothPath(waypoints, segmentsPerSection)
		dashedPolyline(points, color, dashLength, gapLength, width)
	}

	/**
	 * Draw a circle in a plane.
	 *
	 * @param center Center of the circle
	 * @param radius Radius of the circle
	 * @param normal Normal vector of the plane (determines orientation)
	 * @param color Line color
	 * @param segments Number of segments
	 */
	fun circle(
		center: Vec3d,
		radius: Double,
		normal: Vec3d = Vec3d(0.0, 1.0, 0.0),
		color: Color,
		segments: Int = 32,
		width: Float = lineWidth
	) {
		// Create basis vectors perpendicular to normal
		val up =
			if (kotlin.math.abs(normal.y) < 0.99) Vec3d(0.0, 1.0, 0.0)
			else Vec3d(1.0, 0.0, 0.0)
		val u = normal.crossProduct(up).normalize()
		val v = u.crossProduct(normal).normalize()

		val points =
			(0..segments).map { i ->
				val angle = 2.0 * Math.PI * i / segments
				val x = kotlin.math.cos(angle) * radius
				val y = kotlin.math.sin(angle) * radius
				center.add(u.multiply(x)).add(v.multiply(y))
			}

		polyline(points, color, width)
	}

	private fun lineWithWidth(
		x1: Float,
		y1: Float,
		z1: Float,
		x2: Float,
		y2: Float,
		z2: Float,
		color: Color,
		width: Float
	) {
		val dx = x2 - x1
		val dy = y2 - y1
		val dz = z2 - z1
		val len = sqrt(dx * dx + dy * dy + dz * dz)
		val nx = if (len > 0) dx / len else 0f
		val ny = if (len > 0) dy / len else 1f
		val nz = if (len > 0) dz / len else 0f

		collector.addEdgeVertex(x1, y1, z1, color, nx, ny, nz, width)
		collector.addEdgeVertex(x2, y2, z2, color, nx, ny, nz, width)
	}
}
