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
import com.lambda.config.groups.LineConfig
import com.lambda.context.SafeContext
import com.lambda.graphics.outline.OutlineHandler
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.graphics.text.FontHandler
import com.lambda.graphics.text.SDFFontAtlas
import com.lambda.graphics.texture.LambdaImageAtlas
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.hasDirection
import com.lambda.util.BlockUtils.blockState
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.block.BlockState
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.client.render.model.BlockStateModel
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.math.random.Random
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

@DslMarker
annotation class RenderDsl

@Suppress("unused")
@RenderDsl
class RenderBuilder(private val cameraPos: Vec3d, var depthTest: Boolean = false) {
	val collector = RegionVertexCollector()

	var fontAtlas: SDFFontAtlas? = null
		private set

	private var currentLayer = -800f

	private val layerIncrement = 1f

	private val DEFAULT_LIGHT_DIR = Vector3f(0.2f, 1.0f, -0.7f).normalize()
	private val DEFAULT_LIGHT1_DIR = Vector3f(-0.2f, 1.0f, 0.7f).normalize()

	private fun eulerToQuaternion(rot: Vec3d): Quaternionf {
		return Quaternionf().rotationYXZ(
			Math.toRadians(rot.y).toFloat(),
			Math.toRadians(rot.x).toFloat(),
			Math.toRadians(rot.z).toFloat()
		)
	}

	private fun nextLayer(): Float {
		val layer = currentLayer
		currentLayer += layerIncrement
		return layer
	}

	private var activeOutlineId: Int? = null

	fun box(
		box: Box,
		lineConfig: LineConfig? = null,
		builder: (BoxBuilder.() -> Unit)? = null
	) {
		val boxBuilder = BoxBuilder(lineConfig).apply { builder?.invoke(this) }
		if (boxBuilder.fillSides != DirectionMask.NONE) boxBuilder.boxFaces(box)
		if (boxBuilder.outlineSides != DirectionMask.NONE) boxBuilder.boxOutline(box)
	}

	context(safeContext: SafeContext)
	fun boxes(
		pos: BlockPos,
		state: BlockState,
		lineConfig: LineConfig? = null,
		builder: (BoxBuilder.() -> Unit)? = null
	) = with(safeContext) {
		val boxes = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
		val boxBuilder = BoxBuilder(lineConfig).apply { builder?.invoke(this) }
		boxes.forEach { box ->
			if (boxBuilder.fillSides != DirectionMask.NONE) boxBuilder.boxFaces(box)
			if (boxBuilder.outlineSides != DirectionMask.NONE) boxBuilder.boxOutline(box)
		}
	}

	fun box(
		pos: BlockPos,
		lineConfig: LineConfig? = null,
		builder: (BoxBuilder.() -> Unit)? = null
	) = box(Box(pos), lineConfig, builder)

	context(safeContext: SafeContext)
	fun boxes(
		pos: BlockPos,
		lineConfig: LineConfig? = null,
		builder: (BoxBuilder.() -> Unit)? = null
	) = boxes(pos, safeContext.blockState(pos), lineConfig, builder)

	fun filledQuad(
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
		width: Float = -0.0005f,
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
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) = line(x1, y1, z1, x2, y2, z2, c1, c2, width, dashStyle)

	fun line(
		start: Vec3d,
		end: Vec3d,
		color: Color,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) = line(start.x, start.y, start.z, end.x, end.y, end.z, color, color, width, dashStyle)

	fun polyline(
		points: List<Vec3d>,
		color: Color,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		if (points.size < 2) return
		for (i in 0 until points.size - 1) {
			line(points[i], points[i + 1], color, width, dashStyle)
		}
	}

	fun quadraticBezierLine(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		color: Color,
		segments: Int = 16,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.quadraticBezierPoints(p0, p1, p2, segments)
		polyline(points, color, width, dashStyle)
	}

	fun cubicBezierLine(
		p0: Vec3d,
		p1: Vec3d,
		p2: Vec3d,
		p3: Vec3d,
		color: Color,
		segments: Int = 32,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.cubicBezierPoints(p0, p1, p2, p3, segments)
		polyline(points, color, width, dashStyle)
	}

	fun catmullRomSplineLine(
		controlPoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.catmullRomSplinePoints(controlPoints, segmentsPerSection)
		polyline(points, color, width, dashStyle)
	}

	fun smoothLine(
		waypoints: List<Vec3d>,
		color: Color,
		segmentsPerSection: Int = 16,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		val points = CurveUtils.smoothPath(waypoints, segmentsPerSection)
		polyline(points, color, width, dashStyle)
	}

	fun circleLine(
		center: Vec3d,
		radius: Double,
		color: Color,
		width: Float = -0.0005f,
		normal: Vec3d = Vec3d(0.0, 1.0, 0.0),
		segments: Int = 32,
		dashStyle: LineDashStyle? = null
	) {
		val up =
			if (kotlin.math.abs(normal.y) < 0.99) Vec3d(0.0, 1.0, 0.0)
			else Vec3d(1.0, 0.0, 0.0)
		val u = normal.crossProduct(up).normalize()
		val v = u.crossProduct(normal).normalize()

		val points =
			(0..segments).map { i ->
				val angle = 2.0 * Math.PI * i / segments
				val x = cos(angle) * radius
				val y = sin(angle) * radius
				center.add(u.multiply(x)).add(v.multiply(y))
			}

		polyline(points, color, width, dashStyle)
	}

	@JvmName("worldOutline1")
	fun worldOutline(
		entity: Entity,
		style: OutlineStyle
	) = OutlineHandler.setEntityOutline(entity.id, style, depthTest = depthTest)

	@JvmName("worldOutlines1")
	fun worldOutlines(
		entities: Iterable<Entity>,
		style: OutlineStyle
	) = entities.forEach {
		OutlineHandler.setEntityOutline(it.id, style, depthTest = depthTest)
	}

	@JvmName("worldOutline2")
	fun worldOutline(
		pos: BlockPos,
		style: OutlineStyle
	) = OutlineHandler.setBlockOutline(pos, style, depthTest = depthTest)

	@JvmName("worldOutlines2")
	fun worldOutlines(
		positions: Iterable<BlockPos>,
		style: OutlineStyle
	) = positions.forEach {
		OutlineHandler.setBlockOutline(it, style, depthTest = depthTest)
	}

	fun withOutline(style: OutlineStyle, block: RenderBuilder.() -> Unit) {
		val previousId = activeOutlineId
		activeOutlineId = collector.registerCustomOutline(style, depthTest = depthTest)
		try {
			block()
		} finally {
			activeOutlineId = previousId
		}
	}

	fun worldText(
		text: String,
		pos: Vec3d,
		size: Float = 0.5f,
		font: SDFFontAtlas = FontHandler.activeFont,
		style: SDFStyle = SDFStyle(),
		centered: Boolean = true,
		rotation: Vec3d? = null
	) {
		val anchorX = (pos.x - cameraPos.x).toFloat()
		val anchorY = (pos.y - cameraPos.y).toFloat()
		val anchorZ = (pos.z - cameraPos.z).toFloat()

		val textWidth = if (centered) FontHandler.getStringWidthNormalized(text, 1f) else 0f
		val startX = -textWidth / 2f

		val rotationMatrix: Matrix4f? = if (rotation != null) {
			Matrix4f()
				.rotateY(Math.toRadians(rotation.y).toFloat())
				.rotateX(Math.toRadians(rotation.x).toFloat())
				.rotateZ(Math.toRadians(rotation.z).toFloat())
		} else null

		if (style.shadow != null) {
			val shadowColor = style.shadow.color
			val offsetX = style.shadow.offsetX
			val offsetY = style.shadow.offsetY
			buildTextQuads(text, startX + offsetX, offsetY,
				shadowColor.red, shadowColor.green, shadowColor.blue, shadowColor.alpha,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style, font, activeOutlineId, 0)
		}

		if (style.glow != null) {
			val glowColor = style.glow.color
			buildTextQuads(text, startX, 0f,
				glowColor.red, glowColor.green, glowColor.blue, glowColor.alpha,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style, font, activeOutlineId, 1)
		}

		if (style.outline != null) {
			val outlineColor = style.outline.color
			buildTextQuads(text, startX, 0f,
				outlineColor.red, outlineColor.green, outlineColor.blue, outlineColor.alpha,
				anchorX, anchorY, anchorZ, size, rotationMatrix, style, font, activeOutlineId, 2)
		}

		val mainColor = style.color
		buildTextQuads(text, startX, 0f,
			mainColor.red, mainColor.green, mainColor.blue, 255,
			anchorX, anchorY, anchorZ, size, rotationMatrix, style, font, activeOutlineId)
	}

	private val screenWidth get() = mc.window?.scaledWidth?.toFloat() ?: 1920f
	private val screenHeight get() = mc.window?.scaledHeight?.toFloat() ?: 1080f

	private fun toPixelX(normalizedX: Float): Float = normalizedX * screenWidth

	private fun toPixelY(normalizedY: Float): Float = normalizedY * screenHeight

	private fun toPixelSize(normalizedSize: Float): Float =
		normalizedSize * screenHeight

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

	fun screenQuad(
		x1: Float, y1: Float,
		x2: Float, y2: Float,
		x3: Float, y3: Float,
		x4: Float, y4: Float,
		color: Color
	) = screenQuadGradient(x1, y1, color, x2, y2, color, x3, y3, color, x4, y4, color)

	fun screenRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
		val x2 = x + width
		val y2 = y + height
		screenQuad(x, y, x2, y, x2, y2, x, y2, color)
	}

	fun screenRectGradient(
		x: Float, y: Float, width: Float, height: Float,
		topLeft: Color, topRight: Color, bottomRight: Color, bottomLeft: Color
	) {
		val x2 = x + width
		val y2 = y + height
		screenQuadGradient(x, y, topLeft, x2, y, topRight, x2, y2, bottomRight, x, y2, bottomLeft)
	}

	fun screenLineGradient(
		x1: Float, y1: Float, startColor: Color,
		x2: Float, y2: Float, endColor: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) {
		val px1 = toPixelX(x1)
		val py1 = toPixelY(y1)
		val px2 = toPixelX(x2)
		val py2 = toPixelY(y2)
		val pixelWidth = toPixelSize(width)

		val dx = px2 - px1
		val dy = py2 - py1

		val pixelDashStyle = dashStyle?.let {
			LineDashStyle(
				dashLength = toPixelSize(it.dashLength),
				gapLength = toPixelSize(it.gapLength),
				offset = it.offset,
				animated = it.animated,
				animationSpeed = it.animationSpeed
			)
		}

		val layer = nextLayer()

		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px1, py1, startColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle, layer)
		collector.addScreenEdgeVertex(px2, py2, endColor, dx, dy, pixelWidth, pixelDashStyle, layer)
	}

	fun screenLine(
		x1: Float, y1: Float,
		x2: Float, y2: Float,
		color: Color,
		width: Float,
		dashStyle: LineDashStyle? = null
	) = screenLineGradient(x1, y1, color, x2, y2, color, width, dashStyle)

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

		val glintTime = if (hasOverlay) {
			(net.minecraft.util.Util.getMeasuringTimeMs() / 1000.0f) % 1000f
		} else 0f

		val aspectRatio = if (hasOverlay && height != 0f) width / height else 1f

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

	fun model(
		model: BlockModelPart,
		pos: Vec3d,
		scale: Vec3d = Vec3d(1.0, 1.0, 1.0),
		rotation: Quaternionf? = null,
		color: Color = Color.WHITE,
		light: Int = 0xF000F0,
		overlay: Int = OverlayTexture.DEFAULT_UV,
		centered: Boolean = false,
		pixelPerfect: Boolean = false,
		smartAA: Boolean = false,
		shadingAmount: Float = 1.0f
	) {
		val sprite = model.particleSprite() ?: return
		val atlas = sprite.atlasId

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

		var overlayFlag = if (overlay != OverlayTexture.DEFAULT_UV) 1.0f else 0.0f
		if (smartAA) {
			overlayFlag += 2.0f
		}

		val random = Random.create()
		val quads = mutableListOf<BakedQuad>()

		for (direction in net.minecraft.util.math.Direction.entries) {
			random.setSeed(42L)
			quads.addAll(model.getQuads(direction))
		}
		random.setSeed(42L)
		quads.addAll(model.getQuads(null))

		for (quad in quads) {
			val face = quad.face
			var nx = face?.offsetX?.toFloat() ?: 0f
			var ny = face?.offsetY?.toFloat() ?: 0f
			var nz = face?.offsetZ?.toFloat() ?: 0f

			if (face == null) {
				val v0 = quad.getPosition(0)
				val v1 = quad.getPosition(1)
				val v2 = quad.getPosition(2)
				val e1x = v1.x() - v0.x(); val e1y = v1.y() - v0.y(); val e1z = v1.z() - v0.z()
				val e2x = v2.x() - v0.x(); val e2y = v2.y() - v0.y(); val e2z = v2.z() - v0.z()
				nx = e1y * e2z - e1z * e2y
				ny = e1z * e2x - e1x * e2z
				nz = e1x * e2y - e1y * e2x
				val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
				if (len > 0f) { nx /= len; ny /= len; nz /= len }
			}

			for (i in 0 until 4) {
				val posVecSrc = quad.getPosition(i)

				vertexPos.set(posVecSrc.x(), posVecSrc.y(), posVecSrc.z())

				if (centered) vertexPos.sub(0.5f, 0.5f, 0.5f)

				vertexPos.mul(scaleVec)
				rotation?.transform(vertexPos)
				vertexPos.add(posVec)

				normalVec.set(nx, ny, nz)
				rotation?.transform(normalVec)

				val packedUV = quad.getTexcoords(i)
				val u = Float.fromBits((packedUV ushr 32).toInt())
				val v = Float.fromBits((packedUV and 0xFFFFFFFFL).toInt())

				val edgeX = when(i) {
					0 -> 0.0f
					1 -> 1.0f
					2 -> 1.0f
					else -> 0.0f
				}
				val edgeY = when(i) {
					0 -> 0.0f
					1 -> 0.0f
					2 -> 1.0f
					else -> 1.0f
				}

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
			val useNearest = pixelPerfect && !smartAA
			collector.addModelVertices(textureView, vertices, useNearest, activeOutlineId)
		}
	}

	fun worldGuiItem(
		stack: ItemStack,
		pos: Vec3d,
		scale: Float = 0.5f,
		rotation: Vec3d? = null,
		centered: Boolean = true,
		flat: Boolean = true,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null
	) {
		if (stack.isEmpty) return

		val renderState = ItemRenderState()
		mc.itemModelManager.updateForNonLivingEntity(renderState, stack, ItemDisplayContext.GUI, mc.player ?: return)

		val rot = rotation?.let { eulerToQuaternion(it) }
		renderItemState(renderState, pos, scale, rot, centered, isScreen = false, flat = flat, lighting = lighting, overlay = overlay)
	}

	fun screenGuiItem(
		stack: ItemStack,
		x: Float, y: Float,
		size: Float = 0.05f,
		rotation: Vec3d? = null,
		centered: Boolean = true,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null
	) {
		if (stack.isEmpty) return

		val renderState = ItemRenderState()
		mc.itemModelManager.updateForNonLivingEntity(renderState, stack, ItemDisplayContext.GUI, mc.player ?: return)

		val pixelX = toPixelX(x)
		val pixelY = toPixelY(y)
		val pixelSize = toPixelSize(size)

		val rot = rotation?.let { eulerToQuaternion(it) }

		renderItemState(renderState, Vec3d(pixelX.toDouble(), pixelY.toDouble(), nextLayer().toDouble()), pixelSize, rot, centered, isScreen = true, flat = true, lighting = lighting, overlay = overlay)
	}

	private fun renderItemState(
		state: ItemRenderState,
		pos: Vec3d,
		scale: Float,
		rotation: Quaternionf?,
		centered: Boolean,
		isScreen: Boolean,
		flat: Boolean = false,
		lighting: ItemLighting = ItemLighting.VANILLA,
		overlay: ItemOverlay? = null
	) {
		val posVec = if (isScreen) {
			Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
		} else {
			Vector3f((pos.x - cameraPos.x).toFloat(), (pos.y - cameraPos.y).toFloat(), (pos.z - cameraPos.z).toFloat())
		}

		val lightDirs = Pair(Vector3f(lighting.light0), Vector3f(lighting.light1))

		val queue = CapturingQueue(
			posVec, Vector3f(scale), rotation, centered, flat, lighting, lightDirs, state.isSideLit
		) { vertices, textureView ->
			if (isScreen) {
				collector.addScreenModelVertices(textureView, vertices, true)
			} else {
				collector.addModelVertices(textureView, vertices, true, activeOutlineId)
			}
		}

		val matrixStack = MatrixStack()

		for (i in 0 until state.layerCount) {
			val layer = state.layers[i]

			queue.currentGlint = when (overlay) {
				ItemOverlay.DISABLED -> false
				null -> layer.glint != ItemRenderState.Glint.NONE
				else -> true
			}

			matrixStack.push()
			layer.transform.apply(state.displayContext.isLeftHand, matrixStack.peek())

			val specialModel = layer.specialModelType

			if (specialModel != null) {
				specialModel.render(layer.data, state.displayContext, matrixStack, queue, 15728880, 0, queue.currentGlint, 0)
			} else {
				val renderLayer = layer.renderLayer
				if (renderLayer != null) {
					val captureGlint = if (queue.currentGlint) ItemRenderState.Glint.STANDARD else ItemRenderState.Glint.NONE
					queue.submitItem(matrixStack, state.displayContext, 15728880, 0, 0, layer.tints, layer.quads, renderLayer, captureGlint)
				}
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
			val p = Vector3f(this.x, this.y, this.z)

			if (flat) p.z = 0f

			posTransform(p)

			val n = Vector3f(this.nx, this.ny, this.nz)
			normalTransform(n)

			val vIdx = quadBuffer.size
			val qu = if (vIdx == 1 || vIdx == 2) 1f else 0f
			val qv = if (vIdx == 2 || vIdx == 3) 1f else 0f

			val ov = if (currentOverlay != 0) currentOverlay else baseOverlay
			val lgt = if (currentLight != 0) currentLight else baseLight

			quadBuffer.add(RegionVertexCollector.ModelVertex(
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
		private val onSubmission: (List<RegionVertexCollector.ModelVertex>, GpuTextureView) -> Unit
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
			matrices: MatrixStack,
			displayContext: ItemDisplayContext,
			light: Int,
			overlay: Int,
			outlineColors: Int,
			tintLayers: IntArray,
			quads: List<BakedQuad>,
			renderLayer: RenderLayer,
			glintType: ItemRenderState.Glint
		) {
			val sprite = quads.firstOrNull()?.sprite ?: return
			val textureView = mc.textureManager.getTexture(sprite.atlasId)?.glTextureView ?: return

			val vertices = ArrayList<RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f

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

					vertices.add(RegionVertexCollector.ModelVertex(
						vp.x, vp.y, vp.z,
						u, v,
						r, g, b, 255,
						(overlay and 0xFFFF).toFloat(), (overlay ushr 16).toFloat(),
						if (glintType != ItemRenderState.Glint.NONE) 4.0f else 0.0f,
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
			matrices: MatrixStack,
			renderLayer: RenderLayer,
			light: Int,
			overlay: Int,
			tintedColor: Int,
			sprite: Sprite?,
			outlineColor: Int,
			crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?
		) {
			val textureView = if (sprite != null) {
				mc.textureManager.getTexture(sprite.atlasId)?.glTextureView
			} else {
				mc.textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png"))?.glTextureView
			} ?: return

			val vertices = ArrayList<RegionVertexCollector.ModelVertex>()
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
			matrices: MatrixStack,
			renderLayer: RenderLayer,
			light: Int,
			overlay: Int,
			sprite: Sprite?,
			sheeted: Boolean,
			hasGlint: Boolean,
			tintedColor: Int,
			crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?,
			i: Int
		) {
			val textureView = if (sprite != null) {
				mc.textureManager.getTexture(sprite.atlasId)?.glTextureView
			} else {
				mc.textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png"))?.glTextureView
			} ?: return

			val vertices = ArrayList<RegionVertexCollector.ModelVertex>()
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
		override fun submitShadowPieces(matrices: MatrixStack, radius: Float, pieces: List<EntityRenderState.ShadowPiece>) {}
		override fun submitLabel(matrices: MatrixStack, pos: Vec3d?, y: Int, label: Text, ns: Boolean, l: Int, dist: Double, cam: CameraRenderState) {}
		override fun submitText(matrices: MatrixStack, x: Float, y: Float, text: OrderedText, ds: Boolean, lt: TextRenderer.TextLayerType, l: Int, c: Int, bc: Int, oc: Int) {}
		override fun submitFire(matrices: MatrixStack, state: EntityRenderState, rot: Quaternionf) {}
		override fun submitLeash(matrices: MatrixStack, data: EntityRenderState.LeashData) {}
		override fun submitBlock(matrices: MatrixStack, state: BlockState, light: Int, overlay: Int, outlineColor: Int) {
			val model = mc.blockRenderManager.getModel(state)
			val textureView = mc.textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png"))?.glTextureView ?: return

			val vertices = ArrayList<RegionVertexCollector.ModelVertex>()
			val shadingAmount = if (lighting.respectsUseLight && !isSideLit) 0.0f else 1.0f
			val consumer = CapturingConsumer(
				vertices, ::posTransform, ::normalTransform,
				flat, rotation, currentGlint, lightDirs, shadingAmount, light, overlay
			)

			val random = Random.create()
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

		override fun submitMovingBlock(matrices: MatrixStack, state: net.minecraft.client.render.block.MovingBlockRenderState) {
		}
		override fun submitBlockStateModel(matrices: MatrixStack, layer: RenderLayer, model: BlockStateModel, r: Float, g: Float, b: Float, l: Int, o: Int, oc: Int) {}
		override fun submitCustom(matrices: MatrixStack, layer: RenderLayer, renderer: OrderedRenderCommandQueue.Custom) {}
		override fun submitCustom(renderer: OrderedRenderCommandQueue.LayeredCustom) {}
	}


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

		val anchorX = (pos.x - cameraPos.x).toFloat()
		val anchorY = (pos.y - cameraPos.y).toFloat()
		val anchorZ = (pos.z - cameraPos.z).toFloat()

		val halfWidth = size * ratio / 2f
		val halfHeight = size / 2f

		val overlayFlag = if (hasOverlay) 1f else 0f
		val billboardFlag = if (rotation == null) 0f else 1f

		val gx0 = 0f
		val gx1 = 1f
		val gy0 = 0f
		val gy1 = 1f

		val x0 = -halfWidth / size
		val x1 = halfWidth / size
		val y0 = -halfHeight / size
		val y1 = halfHeight / size

		val vertices = if (rotation == null) {
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
		collector.addWorldImageVertices(image.textureView, vertices, pixelPerfect, activeOutlineId)
	}

	fun screenImage(
		texture: Identifier,
		x: Float, y: Float,
		width: Float, height: Float,
		tint: Color = Color.WHITE,
		hasOverlay: Boolean = false,
		pixelPerfect: Boolean = true
	) {
		val imageEntry = LambdaImageAtlas.loadMCTexture(texture) ?: return
		screenImage(imageEntry, x, y, width, height, tint, hasOverlay, pixelPerfect)
	}

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
		val imageEntry = LambdaImageAtlas.loadMCTexture(texture) ?: return
		val ratio = aspectRatio ?: imageEntry.aspectRatio
		worldImage(imageEntry, pos, size, tint, hasOverlay, ratio, rotation, pixelPerfect)
	}

	fun screenText(
		text: String,
		x: Float,
		y: Float,
		size: Float = 0.02f,
		font: SDFFontAtlas = FontHandler.activeFont,
		style: SDFStyle = SDFStyle(),
		centered: Boolean = false
	) {
		val pixelX = toPixelX(x)
		val pixelY = toPixelY(y)

		val targetPixelHeight = toPixelSize(size)

		val pixelSize = targetPixelHeight * font.baseSize / font.ascent

		val normalizedTextWidth = if (centered) font.getStringWidthNormalized(text, size) else 0f
		val textWidth = normalizedTextWidth * screenWidth
		val startX = -textWidth / 2f

		if (style.shadow != null) {
			val shadowColor = style.shadow.color
			val offsetX = style.shadow.offsetX * pixelSize
			val offsetY = -style.shadow.offsetY * pixelSize
			val layer = nextLayer()
			buildScreenTextQuads(font, text, startX + offsetX, offsetY,
				shadowColor.red, shadowColor.green, shadowColor.blue, shadowColor.alpha,
				pixelX, pixelY, pixelSize, style, layer, 0)
		}

		if (style.glow != null) {
			val glowColor = style.glow.color
			val layer = nextLayer()
			buildScreenTextQuads(font, text, startX, 0f,
				glowColor.red, glowColor.green, glowColor.blue, glowColor.alpha,
				pixelX, pixelY, pixelSize, style, layer, 1)
		}

		if (style.outline != null) {
			val outlineColor = style.outline.color
			val layer = nextLayer()
			buildScreenTextQuads(font, text, startX, 0f,
				outlineColor.red, outlineColor.green, outlineColor.blue, outlineColor.alpha,
				pixelX, pixelY, pixelSize, style, layer, 2)
		}

		val mainColor = style.color
		val mainLayer = nextLayer()
		buildScreenTextQuads(font, text, startX, 0f,
			mainColor.red, mainColor.green, mainColor.blue, mainColor.alpha,
			pixelX, pixelY, pixelSize, style, mainLayer, 3)
	}

	private fun buildScreenTextQuads(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,
		startY: Float,
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float,
		pixelSize: Float,
		style: SDFStyle,
		layer: Float,
		layerType: Int = 3
	) {
		val outlineWidth = style.outline?.width ?: 0f
		val glowRadius = style.glow?.radius ?: 0f
		val shadowSoftness = style.shadow?.softness ?: 0f
		val threshold = 0.5f

		var penX = 0f

		for (char in text) {
			val glyph = atlas.getGlyph(char.code) ?: continue

			val localX0 = penX + glyph.bearingX
			val localY1 = glyph.bearingY

			val localX1 = localX0 + glyph.width / atlas.baseSize
			val localY0 = localY1 - glyph.height / atlas.baseSize

			val x0 = anchorX + startX + localX0 * pixelSize
			val y0 = anchorY + startY + localY0 * pixelSize
			val x1 = anchorX + startX + localX1 * pixelSize
			val y1 = anchorY + startY + localY1 * pixelSize

			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x0, y0, layerType, glyph.u0, glyph.v1, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x1, y0, layerType, glyph.u1, glyph.v1, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x1, y1, layerType, glyph.u1, glyph.v0, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
			collector.screenTextVertices.add(RegionVertexCollector.ScreenTextVertex(
				x0, y1, layerType, glyph.u0, glyph.v0, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))

			penX += glyph.advance
		}
	}

	private fun buildTextQuads(
		text: String,
		startX: Float,
		startY: Float,
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float, anchorZ: Float,
		scale: Float,
		rotationMatrix: Matrix4f?,
		style: SDFStyle,
		atlas: SDFFontAtlas = FontHandler.activeFont,
		outlineId: Int? = null,
		layerType: Int = 3
	) {
		val outlineWidth = style.outline?.width ?: 0f
		val glowRadius = style.glow?.radius ?: 0f
		val shadowSoftness = style.shadow?.softness ?: 0f
		val threshold = 0.5f

		var penX = startX
		for (char in text) {
			val glyph = atlas.getGlyph(char.code) ?: continue

			val x0 = penX + glyph.bearingX
			val y0 = startY - glyph.bearingY
			val x1 = x0 + glyph.width / atlas.baseSize
			val y1 = y0 + glyph.height / atlas.baseSize

			if (rotationMatrix == null) {
				collector.addTextVertex(x0, y1, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, true, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(x1, y1, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, true, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(x1, y0, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, true, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(x0, y0, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, true, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
			} else {
				val p0 = transformPoint(rotationMatrix, x0, -y1, 0f)
				val p1 = transformPoint(rotationMatrix, x1, -y1, 0f)
				val p2 = transformPoint(rotationMatrix, x1, -y0, 0f)
				val p3 = transformPoint(rotationMatrix, x0, -y0, 0f)

				collector.addTextVertex(p0.x, p0.y, glyph.u0, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, false, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(p1.x, p1.y, glyph.u1, glyph.v1, r, g, b, a, anchorX, anchorY, anchorZ, scale, false, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(p2.x, p2.y, glyph.u1, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, false, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
				collector.addTextVertex(p3.x, p3.y, glyph.u0, glyph.v0, r, g, b, a, anchorX, anchorY, anchorZ, scale, false, outlineWidth, glowRadius, shadowSoftness, threshold, outlineId, layerType)
			}

			penX += glyph.advance
		}
	}

	private fun BoxBuilder.boxFaces(box: Box) {
		if (fillSides.hasDirection(DirectionMask.EAST)) {
			filledQuadGradient(
				box.maxX, box.minY, box.minZ, fillBottomNorthEast,
				box.maxX, box.maxY, box.minZ, fillTopNorthEast,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast
			)
		}
		if (fillSides.hasDirection(DirectionMask.WEST)) {
			filledQuadGradient(
				box.minX, box.minY, box.minZ, fillBottomNorthWest,
				box.minX, box.minY, box.maxZ, fillBottomSouthWest,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest,
				box.minX, box.maxY, box.minZ, fillTopNorthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.UP)) {
			filledQuadGradient(
				box.minX, box.maxY, box.minZ, fillTopNorthWest,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.maxX, box.maxY, box.minZ, fillTopNorthEast
			)
		}
		if (fillSides.hasDirection(DirectionMask.DOWN)) {
			filledQuadGradient(
				box.minX, box.minY, box.minZ, fillBottomNorthWest,
				box.maxX, box.minY, box.minZ, fillBottomNorthEast,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast,
				box.minX, box.minY, box.maxZ, fillBottomSouthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.SOUTH)) {
			filledQuadGradient(
				box.minX, box.minY, box.maxZ, fillBottomSouthWest,
				box.maxX, box.minY, box.maxZ, fillBottomSouthEast,
				box.maxX, box.maxY, box.maxZ, fillTopSouthEast,
				box.minX, box.maxY, box.maxZ, fillTopSouthWest
			)
		}
		if (fillSides.hasDirection(DirectionMask.NORTH)) {
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

	private fun line(
		x1: Double, y1: Double, z1: Double,
		x2: Double, y2: Double, z2: Double,
		color1: Color,
		color2: Color,
		width: Float = -0.0005f,
		dashStyle: LineDashStyle? = null
	) {
		val rx1 = (x1 - cameraPos.x).toFloat()
		val ry1 = (y1 - cameraPos.y).toFloat()
		val rz1 = (z1 - cameraPos.z).toFloat()
		val rx2 = (x2 - cameraPos.x).toFloat()
		val ry2 = (y2 - cameraPos.y).toFloat()
		val rz2 = (z2 - cameraPos.z).toFloat()

		val dx = rx2 - rx1
		val dy = ry2 - ry1
		val dz = rz2 - rz1

		collector.addEdgeVertex(rx1, ry1, rz1, color1, dx, dy, dz, width, dashStyle, activeOutlineId)
		collector.addEdgeVertex(rx1, ry1, rz1, color1, dx, dy, dz, width, dashStyle, activeOutlineId)
		collector.addEdgeVertex(rx2, ry2, rz2, color2, dx, dy, dz, width, dashStyle, activeOutlineId)
		collector.addEdgeVertex(rx2, ry2, rz2, color2, dx, dy, dz, width, dashStyle, activeOutlineId)
	}

	private fun transformPoint(matrix: Matrix4f, x: Float, y: Float, z: Float): Vector3f {
		val result = Vector4f(x, y, z, 1f)
		matrix.transform(result)
		return Vector3f(result.x, result.y, result.z)
	}

	private fun faceVertex(x: Double, y: Double, z: Double, color: Color) {
		val rx = (x - cameraPos.x).toFloat()
		val ry = (y - cameraPos.y).toFloat()
		val rz = (z - cameraPos.z).toFloat()
		collector.addFaceVertex(rx, ry, rz, color, activeOutlineId)
	}

	data class SDFOutline(
		val color: Color = Color.BLACK,
		val width: Float = 0.1f
	)

	data class SDFGlow(
		val color: Color = Color(0, 200, 255, 180),
		val radius: Float = 0.2f
	)

	data class SDFShadow(
		val color: Color = Color(0, 0, 0, 180),
		val offset: Float = 0.05f,
		val angle: Float = 135f,
		val softness: Float = 0f
	) {
		val offsetX: Float get() = offset * cos(Math.toRadians(angle.toDouble())).toFloat()
		val offsetY: Float get() = offset * sin(Math.toRadians(angle.toDouble())).toFloat()
	}

	data class SDFStyle(
		var color: Color = Color.WHITE,
		val outline: SDFOutline? = null,
		val glow: SDFGlow? = null,
		val shadow: SDFShadow? = SDFShadow()
	)
}