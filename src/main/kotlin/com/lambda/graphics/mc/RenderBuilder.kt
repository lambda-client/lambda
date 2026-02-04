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
import com.lambda.graphics.texture.LambdaImageAtlas
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.hasDirection
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.RenderLayer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
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
	
	/** Current layer depth for screen-space ordering. 
	 * Range: -1000 (far) to 1000 (near) in our orthographic projection.
	 */
	private var currentLayer = 800f
	
	/** Distance between screen layers. Each call moves slightly closer to viewer. */
	private val layerIncrement = 1f
	
	// Vanilla's raw light directions
	private val DEFAULT_LIGHT_DIR = Vector3f(0.2f, 1.0f, -0.7f).normalize()
	private val DEFAULT_LIGHT1_DIR = Vector3f(-0.2f, 1.0f, 0.7f).normalize()

	private fun eulerToQuaternion(rot: Vec3d): Quaternionf {
		return Quaternionf().rotationYXZ(
			Math.toRadians(rot.y).toFloat(),
			Math.toRadians(rot.x).toFloat(),
			Math.toRadians(rot.z).toFloat()
		)
	}

	/** Get next layer depth for screen-space ordering. Later calls render on top. */
	private fun nextLayer(): Float {
		val layer = currentLayer
		currentLayer -= layerIncrement
		return layer
	}

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

	// ============================================================================
	// Image Rendering Methods
	// ============================================================================

	/**
	 * Draw an image on screen at a specific position.
	 * Uses Lambda's custom image rendering pipeline for direct GPU rendering.
	 *
	 * @param image The ImageEntry from LambdaImageAtlas
	 * @param x X position (0-1, normalized screen coordinates)
	 * @param y Y position (0-1, normalized screen coordinates)
	 * @param width Width (0-1, normalized)
	 * @param height Height (0-1, normalized)
	 * @param tint Tint color (default white = no tint)
	 * @param hasOverlay Whether to render an overlay (e.g., enchantment glint)
	 * @param pixelPerfect If true, use NEAREST filtering for crisp pixel art (default: false)
	 */
	fun screenImage(
		image: LambdaImageAtlas.ImageEntry,
		x: Float, y: Float,
		width: Float, height: Float,
		tint: Color = Color.WHITE,
		hasOverlay: Boolean = false,
		pixelPerfect: Boolean = false
	) {
		val layer = nextLayer()
		val x0 = toPixelX(x)
		val y0 = toPixelY(y)
		val x1 = toPixelX(x + width)
		val y1 = toPixelY(y + height)
		
		val overlayFlag = if (hasOverlay) 1f else 0f
		val u0 = image.u0
		val v0 = image.v0
		val u1 = image.u1
		val v1 = image.v1
		
		// Calculate animation time for glint effect
		// Use Util.getMeasuringTimeMs() for consistent timing matching Minecraft's system
		val glintTime = if (hasOverlay) {
			(net.minecraft.util.Util.getMeasuringTimeMs() / 1000.0f) % 1000f  // Seconds, 0-1000 loop
		} else 0f
		
		// Calculate aspect ratio for square glint tiling
		// overlayV carries width/height ratio so shader can correct UVs
		val aspectRatio = if (hasOverlay && height != 0f) width / height else 1f
		
		// Build quad: bottom-left, bottom-right, top-right, top-left (CCW for Y-up)
		// overlayU = animation time, overlayV = aspect ratio
		val vertices = listOf(
			RegionVertexCollector.ScreenImageVertex(
				x0, y0, u0, v1, tint.red, tint.green, tint.blue, tint.alpha,
				glintTime, aspectRatio, overlayFlag, 0f, layer
			),
			RegionVertexCollector.ScreenImageVertex(
				x1, y0, u1, v1, tint.red, tint.green, tint.blue, tint.alpha,
				glintTime, aspectRatio, overlayFlag, 0f, layer
			),
			RegionVertexCollector.ScreenImageVertex(
				x1, y1, u1, v0, tint.red, tint.green, tint.blue, tint.alpha,
				glintTime, aspectRatio, overlayFlag, 0f, layer
			),
			RegionVertexCollector.ScreenImageVertex(
				x0, y1, u0, v0, tint.red, tint.green, tint.blue, tint.alpha,
				glintTime, aspectRatio, overlayFlag, 0f, layer
			)
		)
		collector.addScreenImageVertices(image.textureView, vertices, pixelPerfect)
	}

	// ============================================================================
	// Model Rendering Methods (World Space)
	// ============================================================================



	/**
	 * Render a 3D model in world space.
	 *
	 * @param model The BlockModelPart to render (replaces BakedModel in 1.21.11+)
	 * @param pos World position to render at. If centered is true, this is the center of the model. If false, it's the 0,0,0 corner.
	 * @param scale XML scale
	 * @param rotation Rotation quaternion (around the center if centered=true, else around 0,0,0)
	 * @param color Tint color
	 * @param light Packed light
	 * @param overlay Overlay UV
	 * @param centered If true, shifts model vertices by -0.5 to rotate/scale around the center, then renders at pos.
	 * @param pixelPerfect If true, use NEAREST filtering. If false (default), use LINEAR.
	 * @param smartAA If true, uses shader-based analytic anti-aliasing (Pixel Art AA). Requires pixelPerfect=false (automatically handled).
	 */
	fun model(
		model: net.minecraft.client.render.model.BlockModelPart,
		pos: Vec3d,
		scale: Vec3d = Vec3d(1.0, 1.0, 1.0),
		rotation: org.joml.Quaternionf? = null,
		color: Color = Color.WHITE,
		light: Int = 0xF000F0,
		overlay: Int = net.minecraft.client.render.OverlayTexture.DEFAULT_UV,
		centered: Boolean = false,
		pixelPerfect: Boolean = false,
		smartAA: Boolean = false,
		shadingAmount: Float = 0.0f
	) {
		val sprite = model.particleSprite() ?: return
		val atlas = sprite.atlasId
		// We need to resolve the texture view for the sprite's atlas
		val textureView = mc.textureManager.getTexture(atlas)?.glTextureView ?: return

		val vertices = ArrayList<RegionVertexCollector.ModelVertex>()
		
		val scaleVec = Vector3f(scale.x.toFloat(), scale.y.toFloat(), scale.z.toFloat())
		val posVec = Vector3f(
			(pos.x - cameraPos.x).toFloat(),
			(pos.y - cameraPos.y).toFloat(),
			(pos.z - cameraPos.z).toFloat()
		)
		
		val vertexPos = Vector3f()
		val normalVec = Vector3f()

		val tr = color.red
		val tg = color.green
		val tb = color.blue
		val ta = color.alpha

		val olU = (overlay and 0xFFFF).toFloat()
		val olV = ((overlay shr 16) and 0xFFFF).toFloat()
		
		// Encode Overlay + AA flags into hasOverlay
		// 0 = None, 1 = Overlay, 2 = AA, 3 = Overlay + AA
		var overlayFlag = if (overlay != net.minecraft.client.render.OverlayTexture.DEFAULT_UV) 1.0f else 0.0f
		if (smartAA) {
			overlayFlag += 2.0f
		}
		
		val random = net.minecraft.util.math.random.Random.create()
		val quads = mutableListOf<net.minecraft.client.render.model.BakedQuad>()
		
		// Collect quads for all directions and null direction
		for (direction in net.minecraft.util.math.Direction.entries) {
			random.setSeed(42L)
			quads.addAll(model.getQuads(direction))
		}
		random.setSeed(42L)
		quads.addAll(model.getQuads(null))

		for (quad in quads) {
			// Use face normal for all vertices since BakedQuad doesn't have per-vertex normals easily accessible
			val face = quad.face
			val nx = face.offsetX.toFloat()
			val ny = face.offsetY.toFloat()
			val nz = face.offsetZ.toFloat()

			// Iterate 4 vertices
			for (i in 0 until 4) {
				val posVecSrc = quad.getPosition(i)
				
				// Transform Position
				vertexPos.set(posVecSrc.x(), posVecSrc.y(), posVecSrc.z())
				
				if (centered) {
					// Shift to center (assuming 0..1 model block)
					vertexPos.sub(0.5f, 0.5f, 0.5f)
				}
				
				vertexPos.mul(scaleVec)
				rotation?.transform(vertexPos)
				vertexPos.add(posVec)
				
				// Transform Normal
				normalVec.set(nx, ny, nz)
				rotation?.transform(normalVec)
				
				// Extract UV - Vector2f.toLong packs X (U) in high 32 bits, Y (V) in low 32 bits
				val packedUV = quad.getTexcoords(i)
				val u = Float.fromBits((packedUV ushr 32).toInt())
				val v = Float.fromBits((packedUV and 0xFFFFFFFFL).toInt())
				
				// Edge Data: Map vertex index to a quad corner (0,0 to 1,1)
				// Standard quad winding: 0:0,0 | 1:0,1 | 2:1,1 | 3:1,0 (approx)
				// Reverted to simple bounds without dilation
				val edgeX = if (i == 1 || i == 2) 1.0f else 0.0f
				val edgeY = if (i == 2 || i == 3) 1.0f else 0.0f

				vertices.add(RegionVertexCollector.ModelVertex(
					vertexPos.x, vertexPos.y, vertexPos.z,
					u, v,
					tr, tg, tb, ta,
					olU, olV, overlayFlag, shadingAmount,
					light,
					DEFAULT_LIGHT_DIR.x, DEFAULT_LIGHT_DIR.y, DEFAULT_LIGHT_DIR.z,
					DEFAULT_LIGHT1_DIR.x, DEFAULT_LIGHT1_DIR.y, DEFAULT_LIGHT1_DIR.z,
					normalVec.x, normalVec.y, normalVec.z,
					edgeX, edgeY
				))
			}
		}
		
		if (vertices.isNotEmpty()) {
			// Use Nearest filter only if requested AND Smart AA is NOT used (Smart AA handles sharpness in shader via Linear)
			val useNearest = pixelPerfect && !smartAA
			collector.addModelVertices(textureView, vertices, useNearest)
		}
	} // Default filter?

	fun worldGuiItem(
		stack: ItemStack,
		pos: Vec3d,
		scale: Float = 0.5f,
		rotation: Vec3d? = null,
		centered: Boolean = true,
		flat: Boolean = true,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null,
		glint: Boolean? = null
	) {
		if (stack.isEmpty) return
		
		val renderState = ItemRenderState()
		mc.itemModelManager.updateForNonLivingEntity(renderState, stack, ItemDisplayContext.GUI, mc.player ?: return)
		
		val rot = rotation?.let { eulerToQuaternion(it) }
		renderItemState(renderState, pos, scale, rot, centered, isScreen = false, pixelPerfect = true, flat = flat, lighting = lighting, overlay = overlay, glint = glint)
	}

	fun screenGuiItem(
		stack: ItemStack,
		x: Float, y: Float,
		size: Float = 0.05f,
		rotation: Vec3d? = null,
		centered: Boolean = true,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null,
		glint: Boolean? = null
	) {
		if (stack.isEmpty) return
		
		val renderState = ItemRenderState()
		mc.itemModelManager.updateForNonLivingEntity(renderState, stack, ItemDisplayContext.GUI, mc.player ?: return)
		
		// Convert screen pos to camera-relative "pseudo-world" for the generic renderer
		val pixelX = toPixelX(x)
		val pixelY = toPixelY(y)
		val pixelSize = toPixelSize(size)
		
		val rot = rotation?.let { eulerToQuaternion(it) }
		// Screen items are always flat (viewed straight-on)
		renderItemState(renderState, Vec3d(pixelX.toDouble(), pixelY.toDouble(), nextLayer().toDouble()), pixelSize, rot, centered, isScreen = true, pixelPerfect = true, flat = true, lighting = lighting, overlay = overlay, glint = glint)
	}

	private fun renderItemState(
		state: ItemRenderState,
		pos: Vec3d,
		scale: Float,
		rotation: Quaternionf?,
		centered: Boolean,
		isScreen: Boolean,
		pixelPerfect: Boolean,
		flat: Boolean = false,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null,
		glint: Boolean? = null
	) {
		val posVec = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
		if (!isScreen) {
			posVec.sub(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
		}

		val lightDirs = Pair(Vector3f(lighting.light0), Vector3f(lighting.light1))
		
		val queue = CapturingQueue(
			posVec, Vector3f(scale), rotation, centered, flat, lighting, lightDirs, state.isSideLit
		) { vertices, textureView ->
			if (isScreen) {
				collector.addScreenModelVertices(textureView, vertices, pixelPerfect)
			} else {
				collector.addModelVertices(textureView, vertices, pixelPerfect)
			}
		}

		val matrixStack = net.minecraft.client.util.math.MatrixStack()
		
		for (i in 0 until state.layerCount) {
			@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
			val layer = state.layers[i]
			
			// Refined Glint Logic:
			// 1. If explicit override 'glint' is provided, use it.
			// 2. If 'overlay' is provided (not null), force glint true.
			// 3. Otherwise, base it on the item's own enchantment state (layer.glint != NONE).
			queue.currentGlint = glint ?: (overlay != null || (layer.glint != ItemRenderState.Glint.NONE))
			
			matrixStack.push()
			layer.transform.apply(state.displayContext.isLeftHand, matrixStack.peek())
			
			val specialModel = layer.specialModelType
			val renderLayer = layer.renderLayer
			if (specialModel != null) {
				@Suppress("UNCHECKED_CAST")
				val renderer = specialModel as net.minecraft.client.render.item.model.special.SpecialModelRenderer<Any>
				renderer.render(layer.data, state.displayContext, matrixStack, queue, 15728880, 0, queue.currentGlint, 0)
			} else if (renderLayer != null) {
				// We pass STANDARD if currentGlint is true, otherwise NONE to ensure our capture can correctly toggle it.
				val captureGlint = if (queue.currentGlint) ItemRenderState.Glint.STANDARD else ItemRenderState.Glint.NONE
				queue.submitItem(matrixStack, state.displayContext, 15728880, 0, 0, layer.tints, layer.quads, renderLayer, captureGlint)
			}
			
			matrixStack.pop()
		}
	}

	private class CapturingConsumer(
		private val vertices: MutableList<RegionVertexCollector.ModelVertex>,
		private val posTransform: (Vector3f) -> Unit,
		private val normalTransform: (Vector3f) -> Unit,
		private val flat: Boolean,
		private val rotation: Quaternionf?,
		private val glint: Boolean,
		private val lightDirs: Pair<Vector3f, Vector3f>,
		private val shadingAmount: Float,
		private val baseLight: Int,
		private val baseOverlay: Int
	) : VertexConsumer {
		private var x = 0f; private var y = 0f; private var z = 0f
		private var color = -1
		private var u = 0f; private var v = 0f
		private var currentOverlay = 0
		private var currentLight = 0
		private var nx = 0f; private var ny = 0f; private var nz = 0f
		private val quadBuffer = ArrayList<RegionVertexCollector.ModelVertex>(4)

		override fun vertex(float1: Float, float2: Float, float3: Float): VertexConsumer {
			this.x = float1; this.y = float2; this.z = float3
			return this
		}

		override fun color(argb: Int): VertexConsumer {
			this.color = argb
			return this
		}

		override fun color(r: Int, g: Int, b: Int, a: Int): VertexConsumer {
			this.color = (a shl 24) or (r shl 16) or (g shl 8) or b
			return this
		}

		override fun texture(float1: Float, float2: Float): VertexConsumer {
			this.u = float1; this.v = float2
			return this
		}

		override fun overlay(int1: Int, int2: Int): VertexConsumer {
			this.currentOverlay = (int2 shl 16) or int1
			return this
		}

		override fun light(int1: Int, int2: Int): VertexConsumer {
			this.currentLight = (int2 shl 16) or int1
			return this
		}

		override fun lineWidth(width: Float): VertexConsumer = this

		override fun normal(float1: Float, float2: Float, float3: Float): VertexConsumer {
			this.nx = float1; this.ny = float2; this.nz = float3
			commitVertex()
			return this
		}

		override fun vertex(
			x: Float, y: Float, z: Float,
			color: Int, u: Float, v: Float,
			overlay: Int, light: Int,
			normalX: Float, normalY: Float, normalZ: Float
		) {
			this.x = x; this.y = y; this.z = z
			this.color = color
			this.u = u; this.v = v
			this.currentOverlay = overlay
			this.currentLight = light
			this.nx = normalX; this.ny = normalY; this.nz = normalZ
			commitVertex()
		}

		private fun commitVertex() {
			// 1. Coordinates are already transformed by model parts into "Item Space"
			val p = Vector3f(this.x, this.y, this.z)
			
			// 2. Apply Flattening in Item Space (before Lambda world/screen transforms)
			if (flat) p.z = 0f
			
			// 3. Apply Lambda positioning (ESP world pos or GUI position)
			posTransform(p)

			// 4. Normal handling
			val n = Vector3f(this.nx, this.ny, this.nz)
			normalTransform(n)

			val vIdx = quadBuffer.size
			val qu = if (vIdx == 1 || vIdx == 2) 1f else 0f
			val qv = if (vIdx == 2 || vIdx == 3) 1f else 0f

			val ov = if (currentOverlay != 0) currentOverlay else baseOverlay
			val lgt = if (currentLight != 0) currentLight else baseLight

			quadBuffer.add(com.lambda.graphics.mc.RegionVertexCollector.ModelVertex(
				p.x, p.y, p.z,
				u, v,
				(color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, (color ushr 24) and 0xFF,
				(ov and 0xFFFF).toFloat(), (ov ushr 16).toFloat(),
				if (glint) 4.0f else 0.0f,
				shadingAmount,
				lgt,
				lightDirs.first.x, lightDirs.first.y, lightDirs.first.z,
				lightDirs.second.x, lightDirs.second.y, lightDirs.second.z,
				n.x, n.y, n.z,
				qu, qv
			))

			if (quadBuffer.size == 4) {
				if (flat) {
					// Cull backfaces: The normal is already in Item Space. 
					// Apply Lambda rotation to see if it faces the screen.
					val testN = Vector3f(this.nx, this.ny, this.nz)
					rotation?.transform(testN)
					if (testN.z < 0f) {
						quadBuffer.clear()
						return
					}
				}
				vertices.addAll(quadBuffer)
				quadBuffer.clear()
			}
		}
	}

	private inner class CapturingQueue(
		private val pos: Vector3f,
		private val scale: Vector3f,
		private val rotation: Quaternionf?,
		private val centered: Boolean,
		private val flat: Boolean,
		private val lighting: ItemLighting,
		private val lightDirs: Pair<Vector3f, Vector3f>,
		private val isSideLit: Boolean,
		private val onSubmission: (List<com.lambda.graphics.mc.RegionVertexCollector.ModelVertex>, com.mojang.blaze3d.textures.GpuTextureView) -> Unit
	) : OrderedRenderCommandQueue {
		
		var currentGlint: Boolean = false

		private fun posTransform(v: Vector3f) {
			if (!centered) v.add(0.5f, 0.5f, 0.5f)
			if (flat) v.z = 0f
			v.mul(scale)
			rotation?.transform(v)
			v.add(pos)
		}

		private fun normalTransform(v: Vector3f) {
			rotation?.transform(v)
			v.normalize()
		}

		override fun submitItem(
			matrices: net.minecraft.client.util.math.MatrixStack,
			displayContext: net.minecraft.item.ItemDisplayContext,
			light: Int,
			overlay: Int,
			outlineColors: Int,
			tintLayers: IntArray,
			quads: List<net.minecraft.client.render.model.BakedQuad>,
			renderLayer: net.minecraft.client.render.RenderLayer,
			glintType: net.minecraft.client.render.item.ItemRenderState.Glint
		) {
			val sprite = quads.firstOrNull()?.sprite ?: return
			val textureView = mc.textureManager.getTexture(sprite.atlasId)?.glTextureView ?: return
			
			val vertices = ArrayList<com.lambda.graphics.mc.RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f

			// Transform lights by GUI layer matrix to match normal transformation
			val l0 = Vector3f(lightDirs.first).mulDirection(matrices.peek().positionMatrix)
			val l1 = Vector3f(lightDirs.second).mulDirection(matrices.peek().positionMatrix)
			l0.x = -l0.x; l1.x = -l1.x; l0.normalize(); l1.normalize()

			for (quad in quads) {
				val nx = quad.face.offsetX.toFloat()
				val ny = quad.face.offsetY.toFloat()
				val nz = quad.face.offsetZ.toFloat()

				if (flat) {
					val testN = Vector3f(nx, ny, nz).mulDirection(matrices.peek().positionMatrix)
					rotation?.transform(testN)
					if (testN.z < 0f) continue
				}

				for (vIdx in 0 until 4) {
					val posSrc = quad.getPosition(vIdx)
					val vp = matrices.peek().positionMatrix.transformPosition(posSrc.x(), posSrc.y(), posSrc.z(), Vector3f())
					posTransform(vp)

					val vn = matrices.peek().transformNormal(nx, ny, nz, Vector3f())
					normalTransform(vn)

					val packedUV = quad.getTexcoords(vIdx)
					val u = java.lang.Float.intBitsToFloat((packedUV ushr 32).toInt())
					val v = java.lang.Float.intBitsToFloat((packedUV and 0xFFFFFFFFL).toInt())
					
					val tint = if (quad.hasTint() && quad.tintIndex() < tintLayers.size) tintLayers[quad.tintIndex()] else -1
					val r = if (tint != -1) (tint shr 16 and 0xFF) else 255
					val g = if (tint != -1) (tint shr 8 and 0xFF) else 255
					val b = if (tint != -1) (tint and 0xFF) else 255

					vertices.add(com.lambda.graphics.mc.RegionVertexCollector.ModelVertex(
						vp.x, vp.y, vp.z,
						u, v,
						r, g, b, 255,
						(overlay and 0xFFFF).toFloat(), (overlay ushr 16).toFloat(),
						if (glintType != net.minecraft.client.render.item.ItemRenderState.Glint.NONE) 4.0f else 0.0f,
						shadingAmount,
						light,
						l0.x, l0.y, l0.z,
						l1.x, l1.y, l1.z,
						vn.x, vn.y, vn.z,
						if (vIdx == 1 || vIdx == 2) 1f else 0f,
						if (vIdx == 2 || vIdx == 3) 1f else 0f
					))
				}
			}
			onSubmission(vertices, textureView)
		}

		override fun <S : Any> submitModel(
			model: net.minecraft.client.model.Model<in S>,
			state: S,
			matrices: net.minecraft.client.util.math.MatrixStack,
			renderLayer: net.minecraft.client.render.RenderLayer,
			light: Int,
			overlay: Int,
			tintedColor: Int,
			sprite: net.minecraft.client.texture.Sprite?,
			outlineColor: Int,
			crumblingOverlay: net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand?
		) {
			val textureView = if (sprite != null) {
				mc.textureManager.getTexture(sprite.atlasId)?.glTextureView
			} else {
				mc.textureManager.getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)?.glTextureView
			} ?: return

			val vertices = ArrayList<com.lambda.graphics.mc.RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f
			val consumer = CapturingConsumer(
				vertices, ::posTransform, ::normalTransform,
				flat, rotation, currentGlint, lightDirs, shadingAmount, light, overlay
			)
			val wrappedConsumer = sprite?.getTextureSpecificVertexConsumer(consumer) ?: consumer
			model.setAngles(state)
			model.render(matrices, wrappedConsumer, light, overlay, tintedColor)
			onSubmission(vertices, textureView)
		}

		override fun submitModelPart(
			part: net.minecraft.client.model.ModelPart,
			matrices: net.minecraft.client.util.math.MatrixStack,
			renderLayer: net.minecraft.client.render.RenderLayer,
			light: Int,
			overlay: Int,
			sprite: net.minecraft.client.texture.Sprite?,
			sheeted: Boolean,
			hasGlint: Boolean,
			tintedColor: Int,
			crumblingOverlay: net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand?,
			i: Int
		) {
			val textureView = if (sprite != null) {
				mc.textureManager.getTexture(sprite.atlasId)?.glTextureView
			} else {
				mc.textureManager.getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)?.glTextureView
			} ?: return

			val vertices = ArrayList<com.lambda.graphics.mc.RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f
			val consumer = CapturingConsumer(
				vertices, ::posTransform, ::normalTransform,
				flat, rotation, hasGlint, lightDirs, shadingAmount, light, overlay
			)
			val wrappedConsumer = sprite?.getTextureSpecificVertexConsumer(consumer) ?: consumer
			part.render(matrices, wrappedConsumer, light, overlay, tintedColor)
			onSubmission(vertices, textureView)
		}

		override fun getBatchingQueue(order: Int): net.minecraft.client.render.command.RenderCommandQueue = this
		override fun submitShadowPieces(matrices: net.minecraft.client.util.math.MatrixStack, radius: Float, pieces: List<net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece>) {}
		override fun submitLabel(matrices: net.minecraft.client.util.math.MatrixStack, pos: Vec3d?, y: Int, label: net.minecraft.text.Text, ns: Boolean, l: Int, dist: Double, cam: net.minecraft.client.render.state.CameraRenderState) {}
		override fun submitText(matrices: net.minecraft.client.util.math.MatrixStack, x: Float, y: Float, text: net.minecraft.text.OrderedText, ds: Boolean, lt: net.minecraft.client.font.TextRenderer.TextLayerType, l: Int, c: Int, bc: Int, oc: Int) {}
		override fun submitFire(matrices: net.minecraft.client.util.math.MatrixStack, state: net.minecraft.client.render.entity.state.EntityRenderState, rot: Quaternionf) {}
		override fun submitLeash(matrices: net.minecraft.client.util.math.MatrixStack, data: net.minecraft.client.render.entity.state.EntityRenderState.LeashData) {}
		override fun submitBlock(matrices: net.minecraft.client.util.math.MatrixStack, state: net.minecraft.block.BlockState, light: Int, overlay: Int, outlineColor: Int) {
			val model = mc.blockRenderManager.getModel(state)
			val textureView = mc.textureManager.getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)?.glTextureView ?: return
			
			val vertices = ArrayList<com.lambda.graphics.mc.RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f
			val consumer = CapturingConsumer(
				vertices, ::posTransform, ::normalTransform,
				flat, rotation, currentGlint, lightDirs, shadingAmount, light, overlay
			)
			
			val random = net.minecraft.util.math.random.Random.create()
			val parts = model.getParts(random)
			for (part in parts) {
				for (direction in net.minecraft.util.math.Direction.entries) {
					for (quad in part.getQuads(direction)) {
						consumer.quad(matrices.peek(), quad, 1f, 1f, 1f, 1f, light, overlay)
					}
				}
				for (quad in part.getQuads(null)) {
					consumer.quad(matrices.peek(), quad, 1f, 1f, 1f, 1f, light, overlay)
				}
			}
			
			onSubmission(vertices, textureView)
		}

		override fun submitMovingBlock(matrices: net.minecraft.client.util.math.MatrixStack, state: net.minecraft.client.render.block.MovingBlockRenderState) {
		}
		override fun submitBlockStateModel(matrices: net.minecraft.client.util.math.MatrixStack, layer: RenderLayer, model: net.minecraft.client.render.model.BlockStateModel, r: Float, g: Float, b: Float, l: Int, o: Int, oc: Int) {}
		override fun submitCustom(matrices: net.minecraft.client.util.math.MatrixStack, layer: RenderLayer, renderer: net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom) {}
		override fun submitCustom(renderer: net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom) {}
	}

	/**
	 * Draw a billboard image at a world position.
	 * The image will face the camera by default, or use a custom rotation.
	 *
	 * @param image The ImageEntry from LambdaImageAtlas
	 * @param pos World position for the image
	 * @param size Size in world units
	 * @param tint Tint color (default white = no tint)
	 * @param hasOverlay Whether to render an overlay (e.g., enchantment glint)
	 * @param aspectRatio Width/height ratio (auto-calculated from image if not specified)
	 * @param rotation Custom rotation as Euler angles in degrees (x=pitch, y=yaw, z=roll), null = billboard towards camera
	 * @param pixelPerfect If true, use NEAREST filtering for crisp pixel art (default: false)
	 */
	fun worldImage(
		image: LambdaImageAtlas.ImageEntry,
		pos: Vec3d,
		size: Float = 0.5f,
		tint: Color = Color.WHITE,
		hasOverlay: Boolean = false,
		aspectRatio: Float? = null,
		rotation: Vec3d? = null,
		pixelPerfect: Boolean = false
	) {
		val ratio = aspectRatio ?: image.aspectRatio
		val u0 = image.u0
		val v0 = image.v0
		val u1 = image.u1
		val v1 = image.v1
		
		// Camera-relative anchor position
		val anchorX = (pos.x - cameraPos.x).toFloat()
		val anchorY = (pos.y - cameraPos.y).toFloat()
		val anchorZ = (pos.z - cameraPos.z).toFloat()
		
		// Calculate quad corners (centered on anchor)
		val halfWidth = size * ratio / 2f
		val halfHeight = size / 2f
		
		val overlayFlag = if (hasOverlay) 1f else 0f
		val billboardFlag = if (rotation == null) 0f else 1f
		
		// Quad relative glint UVs (0-1 range)
		val gx0 = 0f
		val gx1 = 1f
		val gy0 = 0f  // 0 at y0 (bottom)
		val gy1 = 1f  // 1 at y1 (top)
		
		// Quad offsets (local space, scaled in shader)
		val x0 = -halfWidth / size
		val x1 = halfWidth / size
		val y0 = -halfHeight / size
		val y1 = halfHeight / size
		
		val vertices = if (rotation == null) {
			// Billboard mode: pass local offsets directly, shader handles billboard
			listOf(
				RegionVertexCollector.WorldImageVertex(
					x0, y0, u0, v1, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx0, gy0, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					x1, y0, u1, v1, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx1, gy0, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					x1, y1, u1, v0, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx1, gy1, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					x0, y1, u0, v0, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx0, gy1, overlayFlag, 0f
				)
			)
		} else {
			// Fixed rotation mode: pre-transform offsets with rotation matrix
			val rotationMatrix = Matrix4f()
				.rotateY(Math.toRadians(rotation.y).toFloat())
				.rotateX(Math.toRadians(rotation.x).toFloat())
				.rotateZ(Math.toRadians(rotation.z).toFloat())
			
			val p0 = transformPoint(rotationMatrix, x0, y0, 0f)
			val p1 = transformPoint(rotationMatrix, x1, y0, 0f)
			val p2 = transformPoint(rotationMatrix, x1, y1, 0f)
			val p3 = transformPoint(rotationMatrix, x0, y1, 0f)
			
			listOf(
				RegionVertexCollector.WorldImageVertex(
					p0.x, p0.y, u0, v1, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx0, gy0, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					p1.x, p1.y, u1, v1, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx1, gy0, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					p2.x, p2.y, u1, v0, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx1, gy1, overlayFlag, 0f
				),
				RegionVertexCollector.WorldImageVertex(
					p3.x, p3.y, u0, v0, tint.red, tint.green, tint.blue, tint.alpha,
					anchorX, anchorY, anchorZ, size, billboardFlag,
					gx0, gy1, overlayFlag, 0f
				)
			)
		}
		collector.addWorldImageVertices(image.textureView, vertices, pixelPerfect)
	}

	// ============================================================================
	// Simplified Image API (Identifier-based)
	// ============================================================================

	/**
	 * Draw a Minecraft texture on screen at a specific position.
	 * The texture is loaded automatically - no UV coordinates needed.
	 *
	 * @param texture Identifier of the texture (e.g., Identifier.ofVanilla("textures/item/diamond.png"))
	 * @param x X position (0-1, normalized screen coordinates)
	 * @param y Y position (0-1, normalized screen coordinates)
	 * @param width Width (0-1, normalized)
	 * @param height Height (0-1, normalized)
	 * @param tint Tint color (default white = no tint)
	 * @param hasOverlay Whether to render an overlay (e.g., enchantment glint)
	 * @param pixelPerfect If true, use NEAREST filtering for crisp pixel art (default: true for MC textures)
	 */
	fun screenImage(
		texture: Identifier,
		x: Float, y: Float,
		width: Float, height: Float,
		tint: Color = Color.WHITE,
		hasOverlay: Boolean = false,
		pixelPerfect: Boolean = true
	) {
		// Load the texture via LambdaImageAtlas
		val imageEntry = LambdaImageAtlas.loadMCTexture(texture) ?: return
		screenImage(imageEntry, x, y, width, height, tint, hasOverlay, pixelPerfect)
	}

	/**
	 * Draw a Minecraft texture as a billboard at a world position.
	 * The texture is loaded automatically - no UV coordinates needed.
	 *
	 * @param texture Identifier of the texture
	 * @param pos World position for the image
	 * @param size Size in world units
	 * @param tint Tint color (default white = no tint)
	 * @param hasOverlay Whether to render an overlay (e.g., enchantment glint)
	 * @param aspectRatio Width/height ratio (for non-square images)
	 * @param rotation Custom rotation, null = billboard towards camera
	 * @param pixelPerfect If true, use NEAREST filtering for crisp pixel art (default: true for MC textures)
	 */
	fun worldImage(
		texture: Identifier,
		pos: Vec3d,
		size: Float = 0.5f,
		tint: Color = Color.WHITE,
		hasOverlay: Boolean = false,
		aspectRatio: Float? = null,
		rotation: Vec3d? = null,
		pixelPerfect: Boolean = true
	) {
		// Load the texture via LambdaImageAtlas
		val imageEntry = LambdaImageAtlas.loadMCTexture(texture) ?: return
		val ratio = aspectRatio ?: imageEntry.aspectRatio
		worldImage(imageEntry, pos, size, tint, hasOverlay, ratio, rotation, pixelPerfect)
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
		val softness: Float = 0f // Shadow blur in SDF units
	) {
		/** X offset computed from angle and distance */
		val offsetX: Float get() = offset * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
		/** Y offset computed from angle and distance */
		val offsetY: Float get() = offset * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
	}

	/** SDF style configuration for text and other SDF-rendered elements */
	data class SDFStyle(
		var color: Color = Color.WHITE,
		val outline: SDFOutline? = null,
		val glow: SDFGlow? = null,
		val shadow: SDFShadow? = SDFShadow() // Default shadow enabled
	)
}