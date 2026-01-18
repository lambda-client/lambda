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
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
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

	/**
	 * Map of TextStyle to lists of text vertices for that style.
	 * Each text piece is grouped by its style to allow rendering with unique SDF params.
	 */
	val textStyleGroups = mutableMapOf<TextStyle, MutableList<RegionVertexCollector.TextVertex>>()
	
	/**
	 * Map of TextStyle to lists of screen text vertices for that style.
	 */
	val screenTextStyleGroups = mutableMapOf<TextStyle, MutableList<RegionVertexCollector.ScreenTextVertex>>()

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
		style: TextStyle = TextStyle(),
		centered: Boolean = true,
		rotation: Vec3d? = null
	) {
		val atlas = font ?: FontHandler.getDefaultFont()
		fontAtlas = atlas

		// Camera-relative anchor position
		val anchorX = (pos.x - cameraPos.x).toFloat()
		val anchorY = (pos.y - cameraPos.y).toFloat()
		val anchorZ = (pos.z - cameraPos.z).toFloat()

		// Calculate text width for centering
		val textWidth = if (centered) atlas.getStringWidth(text, 1f) else 0f
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
	// - (0, 0) = top-left corner
	// - (1, 1) = bottom-right corner
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
	 * By default uses the average of width and height for uniform scaling.
	 * Use toPixelSizeX/Y for non-uniform scaling.
	 */
	private fun toPixelSize(normalizedSize: Float): Float = 
		normalizedSize * (screenWidth + screenHeight) / 2f

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
		collector.addScreenFaceVertex(toPixelX(x1), toPixelY(y1), c1)
		collector.addScreenFaceVertex(toPixelX(x2), toPixelY(y2), c2)
		collector.addScreenFaceVertex(toPixelX(x3), toPixelY(y3), c3)
		collector.addScreenFaceVertex(toPixelX(x4), toPixelY(y4), c4)
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
	 * @param y Top edge (0-1, where 0 = top, 1 = bottom)
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
	 * @param y Top edge (0-1)
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

		// 4 vertices for screen-space line quad
		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle)
		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle)
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
	 * Draw text on screen at a specific position.
	 * Position uses normalized 0-1 range, size is normalized.
	 *
	 * @param text Text to render
	 * @param x X position (0-1, where 0 = left, 1 = right)
	 * @param y Y position (0-1, where 0 = top, 1 = bottom)
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
		style: TextStyle = TextStyle(),
		centered: Boolean = false
	) {
		val atlas = font ?: FontHandler.getDefaultFont()
		fontAtlas = atlas

		// Convert to pixel coordinates
		val pixelX = toPixelX(x)
		val pixelY = toPixelY(y)
		val pixelSize = toPixelSize(size)

		// Calculate text width for centering
		val textWidth = if (centered) atlas.getStringWidth(text, pixelSize) else 0f
		val startX = -textWidth / 2f

		// Render layers in order: shadow -> glow -> outline -> main text
		// Alpha encodes layer type for shader

		// Shadow layer
		if (style.shadow != null) {
			val shadowColor = style.shadow.color
			val offsetX = style.shadow.offsetX * pixelSize
			val offsetY = style.shadow.offsetY * pixelSize
			buildScreenTextQuads(atlas, text, startX + offsetX, offsetY,
				shadowColor.red, shadowColor.green, shadowColor.blue, 25,
				pixelX, pixelY, pixelSize, style)
		}

		// Glow layer
		if (style.glow != null) {
			val glowColor = style.glow.color
			buildScreenTextQuads(atlas, text, startX, 0f,
				glowColor.red, glowColor.green, glowColor.blue, 75,
				pixelX, pixelY, pixelSize, style)
		}

		// Outline layer
		if (style.outline != null) {
			val outlineColor = style.outline.color
			buildScreenTextQuads(atlas, text, startX, 0f,
				outlineColor.red, outlineColor.green, outlineColor.blue, 150,
				pixelX, pixelY, pixelSize, style)
		}

		// Main text layer
		val mainColor = style.color
		buildScreenTextQuads(atlas, text, startX, 0f,
			mainColor.red, mainColor.green, mainColor.blue, 255,
			pixelX, pixelY, pixelSize, style)
	}

	/**
	 * Build screen-space text quad vertices for a layer.
	 * Internal method - uses pixel coordinates.
	 */
	private fun buildScreenTextQuads(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,  // Offset in SCALED pixels (for centering)
		startY: Float,  // Offset in SCALED pixels
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float,
		pixelSize: Float,  // Final text size in pixels
		style: TextStyle
	) {
		// Get or create the vertex list for this style
		val vertices = screenTextStyleGroups.getOrPut(style) { mutableListOf() }
		
		// Glyph metrics (advance, bearingX, bearingY) are ALREADY normalized by baseSize in SDFFontAtlas
		// Glyph width/height are in PIXELS and need to be normalized
		var penX = 0f  // Pen position in normalized units

		for (char in text) {
			val glyph = atlas.getGlyph(char.code) ?: continue

			// bearingX/Y are already normalized, just multiply by pixelSize
			val localX0 = penX + glyph.bearingX
			val localY0 = -glyph.bearingY  // Y flipped for screen (down = positive)
			
			// width/height are in pixels, need normalization
			val localX1 = localX0 + glyph.width / atlas.baseSize
			val localY1 = localY0 + glyph.height / atlas.baseSize

			// Scale to final pixels and add anchor + offsets
			val x0 = anchorX + startX + localX0 * pixelSize
			val y0 = anchorY + startY + localY0 * pixelSize
			val x1 = anchorX + startX + localX1 * pixelSize
			val y1 = anchorY + startY + localY1 * pixelSize

			// Screen-space text uses simple 2D quads
			vertices.add(RegionVertexCollector.ScreenTextVertex(x0, y1, glyph.u0, glyph.v1, r, g, b, a))
			vertices.add(RegionVertexCollector.ScreenTextVertex(x1, y1, glyph.u1, glyph.v1, r, g, b, a))
			vertices.add(RegionVertexCollector.ScreenTextVertex(x1, y0, glyph.u1, glyph.v0, r, g, b, a))
			vertices.add(RegionVertexCollector.ScreenTextVertex(x0, y0, glyph.u0, glyph.v0, r, g, b, a))

			// advance is already normalized, just add it
			penX += glyph.advance
		}
	}

	/**
	 * Build text quad vertices for a layer with specified color and alpha.
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
		style: TextStyle
	) {
		// Get or create the vertex list for this style
		val vertices = textStyleGroups.getOrPut(style) { mutableListOf() }
		
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
				vertices.add(RegionVertexCollector.TextVertex(x0, y1, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f))
				vertices.add(RegionVertexCollector.TextVertex(x1, y1, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f))
				vertices.add(RegionVertexCollector.TextVertex(x1, y0, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f))
				vertices.add(RegionVertexCollector.TextVertex(x0, y0, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 0f))
			} else {
				// Fixed rotation mode: pre-transform offsets with rotation matrix
				// Scale is applied in shader, so we just apply rotation here
				val p0 = transformPoint(rotationMatrix, x0, -y1, 0f)  // Negate Y for flip
				val p1 = transformPoint(rotationMatrix, x1, -y1, 0f)
				val p2 = transformPoint(rotationMatrix, x1, -y0, 0f)
				val p3 = transformPoint(rotationMatrix, x0, -y0, 0f)
				
				vertices.add(RegionVertexCollector.TextVertex(p0.x, p0.y, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f))
				vertices.add(RegionVertexCollector.TextVertex(p1.x, p1.y, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f))
				vertices.add(RegionVertexCollector.TextVertex(p2.x, p2.y, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f))
				vertices.add(RegionVertexCollector.TextVertex(p3.x, p3.y, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, 1f))
			}

			penX += glyph.advance
		}
	}

	private fun BoxBuilder.boxFaces(box: Box) {
	}

	private fun BoxBuilder.boxOutline(box: Box) {
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

	/** Outline effect configuration */
	data class TextOutline(
		val color: Color = Color.BLACK,
		val width: Float = 0.1f // 0.0 - 0.3 in SDF units (distance from edge)
	)

	/** Glow effect configuration */
	data class TextGlow(
		val color: Color = Color(0, 200, 255, 180),
		val radius: Float = 0.2f // Glow spread in SDF units
	)

	/** Shadow effect configuration */
	data class TextShadow(
		val color: Color = Color(0, 0, 0, 180),
		val offset: Float = 0.05f, // Distance in text units
		val angle: Float = 135f, // Angle in degrees: 0=right, 90=down, 180=left, 270=up (default: bottom-right)
		val softness: Float = 0.15f // Shadow blur in SDF units (for documentation, not currently used)
	) {
		/** X offset computed from angle and distance */
		val offsetX: Float get() = offset * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
		/** Y offset computed from angle and distance */
		val offsetY: Float get() = offset * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
	}

	/** Text style configuration */
	data class TextStyle(
		val color: Color = Color.WHITE,
		val outline: TextOutline? = null,
		val glow: TextGlow? = null,
		val shadow: TextShadow? = TextShadow() // Default shadow enabled
	)
}