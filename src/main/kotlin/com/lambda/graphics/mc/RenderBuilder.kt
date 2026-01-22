/*
 * Copyright 2026 Lambda
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
import com.lambda.context.SafeContext
import com.lambda.graphics.text.FontHandler
import com.lambda.graphics.text.SDFFontAtlas
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.hasDirection
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color

@DslMarker
annotation class RenderDsl

@RenderDsl
class RenderBuilder(private val cameraPos: Vec3d) {
	val collector = RegionVertexCollector()

	/** Track font atlas for this builder (for rendering) */
	var fontAtlas: SDFFontAtlas? = null
		private set

	// Style grouping maps removed - style is now embedded in each text vertex

	// ============================================================================
	// Screen-Space Layer Tracking
	// ============================================================================
	// Layer depth for screen-space ordering. Higher values render on top.
	// Range: 0.0 to ~1.0, incrementing with each screen draw call.
	
	/** Current layer depth for screen-space ordering */
	private var currentLayer = 0f
	
	/** Increment per screen draw call (~10,000 possible layers) */
	private val layerIncrement = 0.0001f
	
	/** Get and increment the current layer depth */
	private fun nextLayer(): Float {
		val layer = currentLayer
		currentLayer += layerIncrement
		return layer
	}

	/**
	 * Deferred ItemStack renders to be drawn via Minecraft's DrawContext.
	 * These are rendered after Lambda's geometry for proper layering.
	 */
	val deferredItems = mutableListOf<ScreenItemRender>()

	fun box(
		box: Box,
		lineWidth: Float,
		builder: (BoxBuilder.() -> Unit)? = null
	) {
		val boxBuilder = BoxBuilder(lineWidth).apply { builder?.invoke(this) }
		if (boxBuilder.fillSides != DirectionMask.NONE) boxBuilder.boxFaces(box)
		if (boxBuilder.outlineSides != DirectionMask.NONE) boxBuilder.boxOutline(box)
	}

	context(safeContext: SafeContext)
	fun boxes(
		pos: BlockPos,
		state: BlockState,
		lineWidth: Float,
		builder: (BoxBuilder.() -> Unit)? = null
	) = with(safeContext) {
		val boxes = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
		val boxBuilder = BoxBuilder(lineWidth).apply { builder?.invoke(this) }
		boxes.forEach { box ->
			if (boxBuilder.fillSides != DirectionMask.NONE) boxBuilder.boxFaces(box)
			if (boxBuilder.outlineSides != DirectionMask.NONE) boxBuilder.boxOutline(box)
		}
	}

	fun box(
		pos: BlockPos,
		lineWidth: Float,
		builder: (BoxBuilder.() -> Unit)? = null
	) = box(Box(pos), lineWidth, builder)

	context(safeContext: SafeContext)
	fun boxes(
		pos: BlockPos,
		lineWidth: Float,
		builder: (BoxBuilder.() -> Unit)? = null
	) = boxes(pos, safeContext.blockState(pos), lineWidth, builder)

	fun filledQuadGradient(
		corner1: Vec3d,
		corner2: Vec3d,
		corner3: Vec3d,
		corner4: Vec3d,
		color: Color
	) {
		faceVertex(corner1.x, corner1.y, corner1.z, color)
		faceVertex(corner2.x, corner2.y, corner2.z, color)
		faceVertex(corner3.x, corner3.y, corner3.z, color)
		faceVertex(corner4.x, corner4.y, corner4.z, color)
	}

	fun filledQuadGradient(
		x1: Double, y1: Double, z1: Double, c1: Color,
		x2: Double, y2: Double, z2: Double, c2: Color,
		x3: Double, y3: Double, z3: Double, c3: Color,
		x4: Double, y4: Double, z4: Double, c4: Color
	) {
		faceVertex(x1, y1, z1, c1)
		faceVertex(x2, y2, z2, c2)
		faceVertex(x3, y3, z3, c3)
		faceVertex(x4, y4, z4, c4)
	}

	fun lineGradient(
		startPos: Vec3d, startColor: Color,
		endPos: Vec3d, endColor: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) = lineGradient(
		startPos.x, startPos.y, startPos.z, startColor,
		endPos.x, endPos.y, endPos.z, endColor,
		width,
		dashStyle
	)

	fun lineGradient(
		x1: Double, y1: Double, z1: Double, c1: Color,
		x2: Double, y2: Double, z2: Double, c2: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) = line(x1, y1, z1, x2, y2, z2, c1, c2, width, dashStyle)

	/** Draw a line between two world positions. */
	fun line(
		start: Vec3d,
		end: Vec3d,
		color: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) = line(start.x, start.y, start.z, end.x, end.y, end.z, color, color, width, dashStyle)

	/** Draw a polyline through a list of points. */
	fun polyline(
		points: List<Vec3d>,
		color: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		if (points.size < 2) return
		for (i in 0 until points.size - 1) {
			line(points[i], points[i + 1], color, width, dashStyle)
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
	fun quadraticBezierLine(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		color: Color,
		segments: Int = 16,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.quadraticBezierPoints(p0, p1, p2, segments)
		polyline(points, color, width, dashStyle)
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
	fun cubicBezierLine(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		p3: Vec3d,
		color: Color,
		segments: Int = 32,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.cubicBezierPoints(p0, p1, p2, p3, segments)
		polyline(points, color, width, dashStyle)
	}

	/**
	 * Draw a Catmull-Rom spline that passes through all control points.
	 *
	 * @param controlPoints List of points the spline should pass through (minimum 4)
	 * @param color Line color
	 * @param segmentsPerSection Segments between each pair of control points
	 */
	fun catmullRomSplineLine(
		controlPoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.catmullRomSplinePoints(controlPoints, segmentsPerSection)
		polyline(points, color, width, dashStyle)
	}

	/**
	 * Draw a smooth path through waypoints using Catmull-Rom splines. Handles endpoints
	 * naturally by mirroring.
	 *
	 * @param waypoints List of points to pass through (minimum 2)
	 * @param color Line color
	 * @param segmentsPerSection Smoothness (higher = smoother)
	 */
	fun smoothLine(
		waypoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.smoothPath(waypoints, segmentsPerSection)
		polyline(points, color, width, dashStyle)
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
	fun circleLine(
		center: Vec3d,
		radius: Double,
		normal: Vec3d = Vec3d(0.0, 1.0, 0.0),
		color: Color,
		segments: Int = 32,
		width: Float,
		dashStyle: LineDashStyle? = null
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

		polyline(points, color, width, dashStyle)
	}

	/**
	 * Draw billboard text at a world position.
	 * The text will face the camera by default, or use a custom rotation.
	 *
	 * @param text Text to render
	 * @param pos World position for the text
	 * @param size Size in world units
	 * @param font Font atlas to use (null = default font)
	 * @param style Text style with color and effects (shadow, glow, outline)
	 * @param centered Center text horizontally
	 * @param rotation Custom rotation as Euler angles in degrees (x=pitch, y=yaw, z=roll), null = billboard towards camera
	 */
	fun worldText(
		text: String,
		pos: Vec3d,
		size: Float = 0.5f,
		font: SDFFontAtlas? = null,
		style: SDFStyle = SDFStyle(),
		centered: Boolean = true,
		rotation: Vec3d? = null
	) {
		val atlas = font ?: FontHandler.getDefaultFont()
		fontAtlas = atlas

		// Camera-relative anchor position
		val anchorX = (pos.x - cameraPos.x).toFloat()
		val anchorY = (pos.y - cameraPos.y).toFloat()
		val anchorZ = (pos.z - cameraPos.z).toFloat()

		// Calculate text width for centering (using normalized width which works directly with glyph advances)
		val textWidth = if (centered) atlas.getStringWidthNormalized(text, 1f) else 0f
		val startX = -textWidth / 2f

		// For fixed rotation, we need to build a rotation matrix to pre-transform offsets
		val rotationMatrix: Matrix4f? = if (rotation != null) {
			Matrix4f()
				.rotateY(Math.toRadians(rotation.y).toFloat())
				.rotateX(Math.toRadians(rotation.x).toFloat())
				.rotateZ(Math.toRadians(rotation.z).toFloat())
		} else null

		// Render layers in order: shadow -> glow -> outline -> main text
		// Alpha encodes layer type for shader: <50 = shadow, 50-99 = glow, 100-199 = outline, >=200 = main

		// Shadow layer (alpha < 50 signals shadow)
		if (style.shadow != null) {
			val shadowColor = style.shadow.color
			val offsetX = style.shadow.offsetX
			val offsetY = style.shadow.offsetY
			buildTextQuads(atlas, text, startX + offsetX, offsetY, 
				shadowColor.red, shadowColor.green, shadowColor.blue, 25,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style)
		}

		// Glow layer (alpha 50-99 signals glow)
		if (style.glow != null) {
			val glowColor = style.glow.color
			buildTextQuads(atlas, text, startX, 0f, 
				glowColor.red, glowColor.green, glowColor.blue, 75,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style)
		}

		// Outline layer (alpha 100-199 signals outline)
		if (style.outline != null) {
			val outlineColor = style.outline.color
			buildTextQuads(atlas, text, startX, 0f, 
				outlineColor.red, outlineColor.green, outlineColor.blue, 150,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style)
		}

		// Main text layer (alpha >= 200 signals main text)
		val mainColor = style.color
		buildTextQuads(atlas, text, startX, 0f, 
			mainColor.red, mainColor.green, mainColor.blue, 255,
			anchorX, anchorY, anchorZ, size, rotationMatrix, style)
	}

	// ============================================================================
	// Screen-Space Rendering Methods (Normalized Coordinates)
	// ============================================================================
	// All coordinates use normalized 0-1 range:
	// - (0, 0) = bottom-left corner
	// - (1, 1) = top-right corner
	// - Sizes are also normalized (e.g., 0.1 = 10% of screen dimension)

	/** Get screen width in pixels (uses MC's scaled width). */
	private val screenWidth: Float
		get() = mc.window?.scaledWidth?.toFloat() ?: 1920f

	/** Get screen height in pixels (uses MC's scaled height). */
	private val screenHeight: Float
		get() = mc.window?.scaledHeight?.toFloat() ?: 1080f

	/** Convert normalized X coordinate (0-1) to pixel coordinate. */
	private fun toPixelX(normalizedX: Float): Float = normalizedX * screenWidth

	/** Convert normalized Y coordinate (0-1) to pixel coordinate. */
	private fun toPixelY(normalizedY: Float): Float = normalizedY * screenHeight

	/**
	 * Convert normalized size to pixel size.
	 * Uses height-only scaling to maintain consistent visual size regardless of aspect ratio.
	 * This matches how world-space elements behave when projected to screen.
	 */
	private fun toPixelSize(normalizedSize: Float): Float = 
		normalizedSize * screenHeight

	/**
	 * Draw a filled quad on screen with gradient colors.
	 * All coordinates use normalized 0-1 range.
	 *
	 * @param x1, y1 First corner position (0-1) and color
	 * @param x2, y2 Second corner position (0-1) and color
	 * @param x3, y3 Third corner position (0-1) and color
	 * @param x4, y4 Fourth corner position (0-1) and color
	 */
	fun screenQuadGradient(
		x1: Float, y1: Float, c1: Color,
		x2: Float, y2: Float, c2: Color,
		x3: Float, y3: Float, c3: Color,
		x4: Float, y4: Float, c4: Color
	) {
		val layer = nextLayer()
		collector.addScreenFaceVertex(toPixelX(x1), toPixelY(y1), c1, layer)
		collector.addScreenFaceVertex(toPixelX(x2), toPixelY(y2), c2, layer)
		collector.addScreenFaceVertex(toPixelX(x3), toPixelY(y3), c3, layer)
		collector.addScreenFaceVertex(toPixelX(x4), toPixelY(y4), c4, layer)
	}

	/**
	 * Draw a filled quad on screen with a single color.
	 * All coordinates use normalized 0-1 range.
	 */
	fun screenQuad(
		x1: Float, y1: Float,
		x2: Float, y2: Float,
		x3: Float, y3: Float,
		x4: Float, y4: Float,
		color: Color
	) = screenQuadGradient(x1, y1, color, x2, y2, color, x3, y3, color, x4, y4, color)

	/**
	 * Draw a filled rectangle on screen.
	 * All values use normalized 0-1 range.
	 *
	 * @param x Left edge (0-1, where 0 = left, 1 = right)
	 * @param y Bottom edge (0-1, where 0 = bottom, 1 = top)
	 * @param width Rectangle width (0-1, where 1 = full screen width)
	 * @param height Rectangle height (0-1, where 1 = full screen height)
	 * @param color Fill color
	 */
	fun screenRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
		val x2 = x + width
		val y2 = y + height
		screenQuad(x, y, x2, y, x2, y2, x, y2, color)
	}

	/**
	 * Draw a filled rectangle on screen with gradient colors.
	 * All values use normalized 0-1 range.
	 *
	 * @param x Left edge (0-1)
	 * @param y Bottom edge (0-1, where 0 = bottom, 1 = top)
	 * @param width Rectangle width (0-1)
	 * @param height Rectangle height (0-1)
	 * @param topLeft Color at top-left corner
	 * @param topRight Color at top-right corner
	 * @param bottomRight Color at bottom-right corner
	 * @param bottomLeft Color at bottom-left corner
	 */
	fun screenRectGradient(
		x: Float, y: Float, width: Float, height: Float,
		topLeft: Color, topRight: Color, bottomRight: Color, bottomLeft: Color
	) {
		val x2 = x + width
		val y2 = y + height
		screenQuadGradient(x, y, topLeft, x2, y, topRight, x2, y2, bottomRight, x, y2, bottomLeft)
	}

	/**
	 * Draw a line on screen with gradient colors.
	 * All coordinates use normalized 0-1 range.
	 *
	 * @param x1, y1 Start position (0-1)
	 * @param x2, y2 End position (0-1)
	 * @param startColor Color at start
	 * @param endColor Color at end
	 * @param width Line width (normalized, e.g., 0.005 = 0.5% of screen)
	 * @param dashStyle Optional dash style for dashed lines
	 */
	fun screenLineGradient(
		x1: Float, y1: Float, startColor: Color,
		x2: Float, y2: Float, endColor: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		// Convert to pixels
		val px1 = toPixelX(x1)
		val py1 = toPixelY(y1)
		val px2 = toPixelX(x2)
		val py2 = toPixelY(y2)
		val pixelWidth = toPixelSize(width)

		// Calculate line direction in pixel space
		val dx = px2 - px1
		val dy = py2 - py1

		// Convert dash style lengths to pixels if present
		val pixelDashStyle = dashStyle?.let {
			LineDashStyle(
				dashLength = toPixelSize(it.dashLength),
				gapLength = toPixelSize(it.gapLength),
				offset = it.offset,
				animated = it.animated,
				animationSpeed = it.animationSpeed
			)
		}

		// Get layer for draw order
		val layer = nextLayer()

		// 4 vertices for screen-space line quad
		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle, layer)
	}

	/**
	 * Draw a line on screen with a single color.
	 * All coordinates use normalized 0-1 range.
	 */
	fun screenLine(
		x1: Float, y1: Float,
		x2: Float, y2: Float,
		color: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) = screenLineGradient(x1, y1, color, x2, y2, color, width, dashStyle)

	/**
	 * Queue an ItemStack to be rendered on screen.
	 * Items are rendered after Lambda's geometry using Minecraft's DrawContext.
	 * This provides full-fidelity item rendering with 3D models, enchantment glint,
	 * durability bars, and stack counts.
	 *
	 * @param stack The ItemStack to render
	 * @param x X position (0-1, where 0 = left, 1 = right)
	 * @param y Y position (0-1, where 0 = top, 1 = bottom)
	 * @param size Normalized size using avg of screen dimensions (e.g., 0.03 = ~3%, default ~1.5%)
	 */
	fun screenItem(stack: ItemStack, x: Float, y: Float, size: Float = 0.015f) {
		if (stack.isEmpty) return
		deferredItems.add(ScreenItemRender(stack, x, y, size))
	}

	/**
	 * Draw text on screen at a specific position.
	 * Position uses normalized 0-1 range, size is normalized.
	 *
	 * @param text Text to render
	 * @param x X position (0-1, where 0 = left, 1 = right)
	 * @param y Y position (0-1, where 0 = bottom, 1 = top)
	 * @param size Text size (normalized, e.g., 0.02 = 2% of screen height)
	 * @param font Font atlas to use (null = default font)
	 * @param style Text style with color and effects
	 * @param centered Center text horizontally at the given position
	 */
	fun screenText(
		text: String,
		x: Float,
		y: Float,
		size: Float = 0.02f,
		font: SDFFontAtlas? = null,
		style: SDFStyle = SDFStyle(),
		centered: Boolean = false
	) {
		val atlas = font ?: FontHandler.getDefaultFont()
		fontAtlas = atlas

		// Convert to pixel coordinates
		val pixelX = toPixelX(x)
		val pixelY = toPixelY(y)
		
		// Convert normalized size to target pixel height
		val targetPixelHeight = toPixelSize(size)
		
		// Adjust font size so that text ASCENT (height of capital letters) matches the target pixel height
		// getAscent(fontSize) = ascent / baseSize * fontSize
		// We want: ascent / baseSize * adjustedFontSize = targetPixelHeight
		// So: adjustedFontSize = targetPixelHeight * baseSize / ascent
		val pixelSize = targetPixelHeight * atlas.baseSize / atlas.ascent

		// Calculate text width for centering (normalized width converted to pixels)
		val normalizedTextWidth = if (centered) atlas.getStringWidthNormalized(text, size) else 0f
		val textWidth = normalizedTextWidth * screenWidth
		val startX = -textWidth / 2f

		// Render layers in order: shadow -> glow -> outline -> main text
		// Each layer gets its own draw depth so they render in correct order
		// Alpha encodes layer type for shader

		// Shadow layer
		if (style.shadow != null) {
			val shadowColor = style.shadow.color
			val offsetX = style.shadow.offsetX * pixelSize
			// Negate offsetY for Y-up coordinate system (shadow should appear below text)
			val offsetY = -style.shadow.offsetY * pixelSize
			val layer = nextLayer()
			buildScreenTextQuads(atlas, text, startX + offsetX, offsetY,
				shadowColor.red, shadowColor.green, shadowColor.blue, 25,
				pixelX, pixelY, pixelSize, style, layer)
		}

		// Glow layer
		if (style.glow != null) {
			val glowColor = style.glow.color
			val layer = nextLayer()
			buildScreenTextQuads(atlas, text, startX, 0f,
				glowColor.red, glowColor.green, glowColor.blue, 75,
				pixelX, pixelY, pixelSize, style, layer)
		}

		// Outline layer
		if (style.outline != null) {
			val outlineColor = style.outline.color
			val layer = nextLayer()
			buildScreenTextQuads(atlas, text, startX, 0f,
				outlineColor.red, outlineColor.green, outlineColor.blue, 150,
				pixelX, pixelY, pixelSize, style, layer)
		}

		// Main text layer
		val mainColor = style.color
		val mainLayer = nextLayer()
		buildScreenTextQuads(atlas, text, startX, 0f,
			mainColor.red, mainColor.green, mainColor.blue, 255,
			pixelX, pixelY, pixelSize, style, mainLayer)
	}

	/**
	 * Build screen-space text quad vertices for a layer.
	 * Internal method - uses pixel coordinates. Adds vertices directly to collector.
	 */
	private fun buildScreenTextQuads(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,  // Offset in SCALED pixels (for centering)
		startY: Float,  // Offset in SCALED pixels
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float,
		pixelSize: Float,  // Final text size in pixels
		style: SDFStyle,
		layer: Float  // Layer depth for draw order
	) {
		// Extract SDF style params from SDFStyle object
		val outlineWidth = style.outline?.width ?: 0f
		val glowRadius = style.glow?.radius ?: 0f
		val shadowSoftness = style.shadow?.softness ?: 0f
		val threshold = 0.5f  // Default SDF threshold
		
		// Glyph metrics (advance, bearingX, bearingY) are ALREADY normalized by baseSize in SDFFontAtlas
		// Glyph width/height are in PIXELS and need to be normalized
		var penX = 0f  // Pen position in normalized units

		for (char in text) {
			val glyph = atlas.getGlyph(char.code) ?: continue

			// bearingX/Y are already normalized, just multiply by pixelSize
			// bearingY is the distance from baseline to glyph top, so with Y-up:
			// - glyph top is at baseline + bearingY
			// - glyph bottom is at baseline + bearingY - height
			val localX0 = penX + glyph.bearingX
			val localY1 = glyph.bearingY  // Top of glyph (Y-up)
			
			// width/height are in pixels, need normalization
			val localX1 = localX0 + glyph.width / atlas.baseSize
			val localY0 = localY1 - glyph.height / atlas.baseSize  // Bottom of glyph

			// Scale to final pixels and add anchor + offsets
			val x0 = anchorX + startX + localX0 * pixelSize
			val y0 = anchorY + startY + localY0 * pixelSize
			val x1 = anchorX + startX + localX1 * pixelSize
			val y1 = anchorY + startY + localY1 * pixelSize

			// Screen-space text uses simple 2D quads - add directly to collector with style params
			// Quad winding: bottom-left, bottom-right, top-right, top-left (CCW for Y-up)
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x0, y0, glyph.u0, glyph.v1, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x1, y0, glyph.u1, glyph.v1, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x1, y1, glyph.u1, glyph.v0, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x0, y1, glyph.u0, glyph.v0, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))

			// advance is already normalized, just add it
			penX += glyph.advance
		}
	}

	/**
	 * Build text quad vertices for a layer with specified color and alpha.
	 * Adds vertices directly to collector with embedded SDF style params.
	 * 
	 * @param atlas Font atlas
	 * @param text Text string
	 * @param startX Starting X offset for text
	 * @param startY Starting Y offset for text
	 * @param r Red color component
	 * @param g Green color component
	 * @param b Blue color component
	 * @param a Alpha component (encodes layer type)
	 * @param anchorX Camera-relative anchor X position
	 * @param anchorY Camera-relative anchor Y position
	 * @param anchorZ Camera-relative anchor Z position
	 * @param scale Text scale
	 * @param rotationMatrix Optional rotation matrix for fixed rotation mode
	 */
	private fun buildTextQuads(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,
		startY: Float,
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float, anchorZ: Float,
		scale: Float,
		rotationMatrix: Matrix4f?,
		style: SDFStyle
	) {
		// Extract SDF style params from SDFStyle object
		val outlineWidth = style.outline?.width ?: 0f
		val glowRadius = style.glow?.radius ?: 0f
		val shadowSoftness = style.shadow?.softness ?: 0f
		val threshold = 0.5f  // Default SDF threshold
		
		var penX = startX
		for (char in text) {
			val glyph = atlas.getGlyph(char.code) ?: continue

			val x0 = penX + glyph.bearingX
			val y0 = startY - glyph.bearingY
			val x1 = x0 + glyph.width / atlas.baseSize
			val y1 = y0 + glyph.height / atlas.baseSize

			if (rotationMatrix == null) {
				// Billboard mode: pass local offsets directly, shader handles billboard
				// Bottom-left, Bottom-right, Top-right, Top-left
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					x0, y1, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					x1, y1, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					x1, y0, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					x0, y0, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
			} else {
				// Fixed rotation mode: pre-transform offsets with rotation matrix
				// Scale is applied in shader, so we just apply rotation here
				val p0 = transformPoint(rotationMatrix, x0, -y1, 0f)  // Negate Y for flip
				val p1 = transformPoint(rotationMatrix, x1, -y1, 0f)
				val p2 = transformPoint(rotationMatrix, x1, -y0, 0f)
				val p3 = transformPoint(rotationMatrix, x0, -y0, 0f)
				
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					p0.x, p0.y, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					p1.x, p1.y, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					p2.x, p2.y, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
				collector.textVertices.add(RegionVertexCollector.TextVertex(
					p3.x, p3.y, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f,
					outlineWidth, glowRadius, shadowSoftness, threshold))
			}

			penX += glyph.advance
		}
	}

	private fun BoxBuilder.boxFaces(box: Box) {
		// We need to call the internal methods, so we'll use filled() with interpolated colors
		// For per-vertex colors on faces, we need direct access to the collector

		if (fillSides.hasDirection(DirectionMask.EAST)) {
			// East face (+X): uses NE and SE corners
			filledQuadGradient(
				box.maxX, box.minY, box.minZ, fillBottomNorthEast,
				box.maxX, box.maxY, box.minZ, fillTopNorthEast,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast
			)
		}
		if (fillSides.hasDirection(DirectionMask.WEST)) {
			// West face (-X): uses NW and SW corners
			filledQuadGradient(
				box.minX, box.minY, box.minZ, fillBottomNorthWest,
				box.minX, box.minY, box.maxZ, fillBottomSouthWest,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest,
				box.minX, box.maxY, box.minZ, fillTopNorthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.UP)) {
			// Top face (+Y): uses all top corners
			filledQuadGradient(
				box.minX, box.maxY, box.minZ, fillTopNorthWest,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.maxX, box.maxY, box.minZ, fillTopNorthEast
			)
		}
		if (fillSides.hasDirection(DirectionMask.DOWN)) {
			// Bottom face (-Y): uses all bottom corners
			filledQuadGradient(
				box.minX, box.minY, box.minZ, fillBottomNorthWest,
				box.maxX, box.minY, box.minZ, fillBottomNorthEast,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast,
				box.minX, box.minY, box.maxZ, fillBottomSouthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.SOUTH)) {
			// South face (+Z): uses SW and SE corners
			filledQuadGradient(
				box.minX, box.minY, box.maxZ, fillBottomSouthWest,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.NORTH)) {
			// North face (-Z): uses NW and NE corners
			filledQuadGradient(
				box.minX, box.minY, box.minZ, fillBottomNorthWest,
				box.minX, box.maxY, box.minZ, fillTopNorthWest,
				box.maxX, box.maxY, box.minZ, fillTopNorthEast,
				box.maxX, box.minY, box.minZ, fillBottomNorthEast
			)
		}
	}

	private fun BoxBuilder.boxOutline(box: Box) {
		val hasEast = outlineSides.hasDirection(DirectionMask.EAST)
		val hasWest = outlineSides.hasDirection(DirectionMask.WEST)
		val hasUp = outlineSides.hasDirection(DirectionMask.UP)
		val hasDown = outlineSides.hasDirection(DirectionMask.DOWN)
		val hasSouth = outlineSides.hasDirection(DirectionMask.SOUTH)
		val hasNorth = outlineSides.hasDirection(DirectionMask.NORTH)

		// Top edges (all use top vertex colors)
		if (outlineMode.check(hasUp, hasNorth)) {
			lineGradient(
				box.minX, box.maxY, box.minZ, outlineTopNorthWest,
				box.maxX, box.maxY, box.minZ, outlineTopNorthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasUp, hasSouth)) {
			lineGradient(
				box.minX, box.maxY, box.maxZ, outlineTopSouthWest,
				box.maxX, box.maxY, box.maxZ, outlineTopSouthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasUp, hasWest)) {
			lineGradient(
				box.minX, box.maxY, box.minZ, outlineTopNorthWest,
				box.minX, box.maxY, box.maxZ, outlineTopSouthWest,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasUp, hasEast)) {
			lineGradient(
				box.maxX, box.maxY, box.maxZ, outlineTopSouthEast,
				box.maxX, box.maxY, box.minZ, outlineTopNorthEast,
				lineWidth, dashStyle
			)
		}

		// Bottom edges (all use bottom vertex colors)
		if (outlineMode.check(hasDown, hasNorth)) {
			lineGradient(
				box.minX, box.minY, box.minZ, outlineBottomNorthWest,
				box.maxX, box.minY, box.minZ, outlineBottomNorthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasDown, hasSouth)) {
			lineGradient(
				box.minX, box.minY, box.maxZ, outlineBottomSouthWest,
				box.maxX, box.minY, box.maxZ, outlineBottomSouthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasDown, hasWest)) {
			lineGradient(
				box.minX, box.minY, box.minZ, outlineBottomNorthWest,
				box.minX, box.minY, box.maxZ, outlineBottomSouthWest,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasDown, hasEast)) {
			lineGradient(
				box.maxX, box.minY, box.minZ, outlineBottomNorthEast,
				box.maxX, box.minY, box.maxZ, outlineBottomSouthEast,
				lineWidth, dashStyle
			)
		}

		// Vertical edges (gradient from top to bottom)
		if (outlineMode.check(hasWest, hasNorth)) {
			lineGradient(
				box.minX, box.maxY, box.minZ, outlineTopNorthWest,
				box.minX, box.minY, box.minZ, outlineBottomNorthWest,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasNorth, hasEast)) {
			lineGradient(
				box.maxX, box.maxY, box.minZ, outlineTopNorthEast,
				box.maxX, box.minY, box.minZ, outlineBottomNorthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasEast, hasSouth)) {
			lineGradient(
				box.maxX, box.maxY, box.maxZ, outlineTopSouthEast,
				box.maxX, box.minY, box.maxZ, outlineBottomSouthEast,
				lineWidth, dashStyle
			)
		}
		if (outlineMode.check(hasSouth, hasWest)) {
			lineGradient(
				box.minX, box.maxY, box.maxZ, outlineTopSouthWest,
				box.minX, box.minY, box.maxZ, outlineBottomSouthWest,
				lineWidth, dashStyle
			)
		}
	}

	/** Draw a line with world coordinates - handles relative conversion internally */
	private fun line(
		x1: Double, y1: Double, z1: Double,
		x2: Double, y2: Double, z2: Double,
		color1: Color,
		color2: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		// Convert to camera-relative coordinates
		val rx1 = (x1 - cameraPos.x).toFloat()
		val ry1 = (y1 - cameraPos.y).toFloat()
		val rz1 = (z1 - cameraPos.z).toFloat()
		val rx2 = (x2 - cameraPos.x).toFloat()
		val ry2 = (y2 - cameraPos.y).toFloat()
		val rz2 = (z2 - cameraPos.z).toFloat()

		// Calculate segment vector
		val dx = rx2 - rx1
		val dy = ry2 - ry1
		val dz = rz2 - rz1

		// Quad-based lines need 4 vertices per segment
		collector.addEdgeVertex(rx1, ry1, rz1, color1, dx, dy, dz, width, dashStyle)
		collector.addEdgeVertex(rx1, ry1, rz1, color1, dx, dy, dz, width, dashStyle)
		collector.addEdgeVertex(rx2, ry2, rz2, color2, dx, dy, dz, width, dashStyle)
		collector.addEdgeVertex(rx2, ry2, rz2, color2, dx, dy, dz, width, dashStyle)
	}

	/** Helper to transform a point by a matrix */
	private fun transformPoint(matrix: Matrix4f, x: Float, y: Float, z: Float): Vector3f {
		val result = Vector4f(x, y, z, 1f)
		matrix.transform(result)
		return Vector3f(result.x, result.y, result.z)
	}

	/** Add a face vertex with world coordinates - handles relative conversion internally */
	private fun faceVertex(x: Double, y: Double, z: Double, color: Color) {
		val rx = (x - cameraPos.x).toFloat()
		val ry = (y - cameraPos.y).toFloat()
		val rz = (z - cameraPos.z).toFloat()
		collector.addFaceVertex(rx, ry, rz, color)
	}

	/** SDF outline effect configuration */
	data class SDFOutline(
		val color: Color = Color.BLACK,
		val width: Float = 0.1f // 0.0 - 0.3 in SDF units (distance from edge)
	)

	/** SDF glow effect configuration */
	data class SDFGlow(
		val color: Color = Color(0, 200, 255, 180),
		val radius: Float = 0.2f // Glow spread in SDF units
	)

	/** SDF shadow effect configuration */
	data class SDFShadow(
		val color: Color = Color(0, 0, 0, 180),
		val offset: Float = 0.05f, // Distance in text units
		// Angle in degrees: 0=right, 90=up, 180=left, 270=down (for screen text with Y-up)
		// For world text, angle is applied in local text space before billboarding
		val angle: Float = 135f, // Default: bottom-right (45° below horizontal)
		val softness: Float = 0.15f // Shadow blur in SDF units
	) {
		/** X offset computed from angle and distance */
		val offsetX: Float get() = offset * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
		/** Y offset computed from angle and distance */
		val offsetY: Float get() = offset * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
	}

	/** SDF style configuration for text and other SDF-rendered elements */
	data class SDFStyle(
		val color: Color = Color.WHITE,
		val outline: SDFOutline? = null,
		val glow: SDFGlow? = null,
		val shadow: SDFShadow? = SDFShadow() // Default shadow enabled
	)

	/** Data class for deferred screen-space item rendering. */
	data class ScreenItemRender(
		val stack: ItemStack,
		val x: Float,      // Normalized 0-1
		val y: Float,      // Normalized 0-1
		val size: Float    // Normalized size (e.g., 0.05 = 5% of screen height)
	)
}