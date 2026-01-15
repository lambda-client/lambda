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

package com.lambda.graphics.text

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.RegionRenderer
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.BufferAllocator
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * High-quality SDF-based text renderer with anti-aliasing and effects.
 *
 * Features:
 * - **Scalable**: Crisp text at any size without pixelation
 * - **Anti-aliased**: Smooth edges via SDF sampling
 * - **Outline**: Configurable outline color and width
 * - **Glow**: Soft outer glow effect
 * - **Shadow**: Drop shadow support
 *
 * Usage:
 * ```kotlin
 * // Load a font (once during init)
 * val font = SDFTextRenderer.loadFont("fonts/FiraSans-Regular.ttf")
 *
 * // Render with effects
 * SDFTextRenderer.drawWorld(
 *     font = font,
 *     text = "Player Name",
 *     pos = entity.eyePos.add(0.0, 0.5, 0.0),
 *     fontSize = 0.5f,
 *     style = TextStyle(
 *         color = Color.WHITE,
 *         outline = TextOutline(Color.BLACK, 0.1f),
 *         glow = TextGlow(Color.CYAN, 0.2f)
 *     )
 * )
 * ```
 */
object SDFTextRenderer {
	private val fonts = ConcurrentHashMap<String, SDFFontAtlas>()
	private var defaultFont: SDFFontAtlas? = null

	/** Outline effect configuration */
	data class TextOutline(
		val color: Color = Color.BLACK,
		val width: Float = 0.1f // 0.0 - 0.5 in SDF units
	)

	/** Glow effect configuration */
	data class TextGlow(
		val color: Color = Color(0, 200, 255, 180),
		val radius: Float = 0.15f // Glow spread in SDF units
	)

	/** Text style configuration */
	data class TextStyle(
		val color: Color = Color.WHITE,
		val outline: TextOutline? = null,
		val glow: TextGlow? = null,
		val shadow: Boolean = true,
		val shadowColor: Color = Color(0, 0, 0, 180),
		val shadowOffset: Float = 0.05f
	)

	/**
	 * Load a font from resources.
	 *
	 * @param path Resource path to TTF/OTF file (e.g., "fonts/FiraSans-Regular.ttf")
	 * @param size Font size in pixels
	 * @return The loaded FontAtlas, or null if loading failed
	 */
	fun loadFont(path: String, size: Float = 256f): SDFFontAtlas? {
		val key = "$path@$size"
		return fonts.getOrPut(key) {
			try {
				// Don't call upload() here - it requires render thread
				// upload() is called lazily in drawTextQuads when textureId == 0
				SDFFontAtlas(path, size)
			} catch (e: Exception) {
				System.err.println("[TextRenderer] Failed to load font: $path")
				System.err.println("[TextRenderer] Full path attempted: /assets/lambda/$path")
				e.printStackTrace()
				return null
			}
		}
	}

	/**
	 * Get or create the default font.
	 * Size should match SDFFontAtlas defaults (128) to prevent atlas overflow.
	 */
	fun getDefaultFont(size: Float = 128f): SDFFontAtlas {
		defaultFont?.let { return it }

		// Try to load without catching, so the actual exception is visible
		val key = "fonts/MinecraftDefault-Regular.ttf@$size"
		val font = fonts[key] ?: run {
			val newFont = SDFFontAtlas("fonts/MinecraftDefault-Regular.ttf", size)
			fonts[key] = newFont
			newFont
		}
		defaultFont = font
		return font
	}

	/**
	 * Draw text at a world position (billboard style).
	 *
	 * @param font SDF font atlas to use
	 * @param text Text to render
	 * @param pos World position
	 * @param fontSize Size in world units
	 * @param style Text styling (color, outline, glow, shadow)
	 * @param centered Center text horizontally
	 * @param seeThrough Render through walls
	 */
	fun drawWorld(
		font: SDFFontAtlas? = null,
		text: String,
		pos: Vec3d,
		fontSize: Float = 0.5f,
		style: TextStyle = TextStyle(),
		centered: Boolean = true,
		seeThrough: Boolean = false
	) {
		val atlas = font ?: getDefaultFont()
		val camera = mc.gameRenderer?.camera ?: return
		val cameraPos = camera.pos

		// Camera-relative position
		val relX = (pos.x - cameraPos.x).toFloat()
		val relY = (pos.y - cameraPos.y).toFloat()
		val relZ = (pos.z - cameraPos.z).toFloat()

		// Build billboard model matrix
		val modelMatrix = Matrix4f()
			.translate(relX, relY, relZ)
			.rotate(camera.rotation)
			.scale(fontSize, -fontSize, fontSize)

		val textWidth = if (centered) atlas.getStringWidth(text, 1f) else 0f
		val startX = -textWidth / 2f

		// Draw shadow first (offset, alpha < 50 signals shadow layer)
		if (style.shadow) {
			val shadowColor = Color(style.shadowColor.red, style.shadowColor.green, style.shadowColor.blue, 25)
			renderTextLayer(
				atlas, text, startX + style.shadowOffset, style.shadowOffset,
				shadowColor, modelMatrix, seeThrough
			)
		}

		// Draw glow layer (alpha 50-99 signals glow layer)
		if (style.glow != null) {
			val glowColor = Color(style.glow.color.red, style.glow.color.green, style.glow.color.blue, 75)
			renderTextLayer(
				atlas, text, startX, 0f,
				glowColor, modelMatrix, seeThrough
			)
		}

		// Draw outline layer (alpha 100-199 signals outline layer)
		if (style.outline != null) {
			val outlineColor = Color(style.outline.color.red, style.outline.color.green, style.outline.color.blue, 150)
			renderTextLayer(
				atlas, text, startX, 0f,
				outlineColor, modelMatrix, seeThrough
			)
		}

		// Draw main text (alpha >= 200 signals main text layer)
		val mainColor = Color(style.color.red, style.color.green, style.color.blue, 255)
		renderTextLayer(
			atlas, text, startX, 0f,
			mainColor, modelMatrix, seeThrough
		)
	}

	/**
	 * Draw text on screen at pixel coordinates.
	 */
	fun drawScreen(
		font: SDFFontAtlas? = null,
		text: String,
		x: Float,
		y: Float,
		fontSize: Float = 16f,
		style: TextStyle = TextStyle()
	) {
		val atlas = font ?: getDefaultFont()
		val scale = fontSize / atlas.baseSize

		// Create orthographic model matrix
		val modelMatrix = Matrix4f()
			.translate(x, y, 0f)
			.scale(scale, scale, 1f)

		// Use screen-space rendering
		if (style.shadow) {
			renderTextLayerScreen(
				atlas, text, style.shadowOffset * fontSize, style.shadowOffset * fontSize,
				style.shadowColor, modelMatrix
			)
		}

		if (style.outline != null) {
			renderTextLayerScreen(
				atlas, text, 0f, 0f,
				style.outline.color, modelMatrix
			)
		}

		if (style.glow != null) {
			renderTextLayerScreen(
				atlas, text, 0f, 0f,
				style.glow.color, modelMatrix
			)
		}

		renderTextLayerScreen(
			atlas, text, 0f, 0f,
			style.color, modelMatrix
		)
	}

	/**
	 * Draw text at a world position projected to screen.
	 */
	fun drawWorldToScreen(
		font: SDFFontAtlas? = null,
		text: String,
		worldPos: Vec3d,
		fontSize: Float = 16f,
		style: TextStyle = TextStyle(),
		offsetY: Float = 0f
	) {
		val screenPos = RenderMain.worldToScreen(worldPos) ?: return
		drawScreen(font, text, screenPos.x, screenPos.y + offsetY, fontSize, style)
	}

	private fun renderTextLayer(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,
		startY: Float,
		color: Color,
		modelMatrix: Matrix4f,
		seeThrough: Boolean
	) {
		if (!atlas.isUploaded) atlas.upload()
		val textureView = atlas.textureView ?: return
		val sampler = atlas.sampler ?: return
		if (text.isEmpty()) return

		// Build vertices for all glyphs
		val vertices = buildTextVertices(atlas, text, startX, startY, color)
		if (vertices.isEmpty()) return

		// Upload to GPU buffer
		val gpuBuffer = uploadTextVertices(vertices) ?: return

		// Use SDF_TEXT pipeline for proper smoothstep anti-aliasing
		val pipeline = if (seeThrough) LambdaRenderPipelines.SDF_TEXT_THROUGH
		else LambdaRenderPipelines.SDF_TEXT

		// Calculate model-view uniform (projection is handled by bindDefaultUniforms)
		val modelView = Matrix4f(RenderMain.modelViewMatrix).mul(modelMatrix)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f())

		RegionRenderer.createRenderPass("SDF Text", useDepth = !seeThrough)?.use { pass ->
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)

			// Bind texture using MC 1.21's proper API
			pass.bindTexture("Sampler0", textureView, sampler)

			// Draw
			pass.setVertexBuffer(0, gpuBuffer)
			val indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
			// For QUADS mode, each quad (4 vertices) needs 6 indices (2 triangles)
			val quadCount = vertices.size / 4
			val indexCount = quadCount * 6
			pass.setIndexBuffer(indexBuffer.getIndexBuffer(indexCount), indexBuffer.indexType)
			pass.drawIndexed(0, 0, indexCount, 1)
		}

		gpuBuffer.close()
	}

	private fun renderTextLayerScreen(
		atlas: SDFFontAtlas,
		text: String,
		offsetX: Float,
		offsetY: Float,
		color: Color,
		modelMatrix: Matrix4f
	) {
		if (!atlas.isUploaded) atlas.upload()
		val textureView = atlas.textureView ?: return
		val sampler = atlas.sampler ?: return
		if (text.isEmpty()) return

		val vertices = buildTextVertices(atlas, text, offsetX, offsetY, color)
		if (vertices.isEmpty()) return

		val gpuBuffer = uploadTextVertices(vertices) ?: return

		val window = mc.window
		val ortho = Matrix4f().ortho(
			0f, window.scaledWidth.toFloat(),
			window.scaledHeight.toFloat(), 0f,
			-1000f, 1000f
		)

		// Calculate MVP and dynamic uniforms BEFORE opening render pass
		val mvp = Matrix4f(ortho).mul(modelMatrix)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(mvp, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f())

		RegionRenderer.createRenderPass("SDF Text Screen", useDepth = false)?.use { pass ->
			pass.setPipeline(LambdaRenderPipelines.SDF_TEXT_THROUGH)
			// Note: not calling bindDefaultUniforms - we provide complete MVP in DynamicTransforms
			pass.setUniform("DynamicTransforms", dynamicTransform)

			// Bind texture using MC 1.21's proper API
			pass.bindTexture("Sampler0", textureView, sampler)

			pass.setVertexBuffer(0, gpuBuffer)
			val indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
			// For QUADS mode, each quad (4 vertices) needs 6 indices (2 triangles)
			val quadCount = vertices.size / 4
			val indexCount = quadCount * 6
			pass.setIndexBuffer(indexBuffer.getIndexBuffer(indexCount), indexBuffer.indexType)
			pass.drawIndexed(0, 0, indexCount, 1)
		}

		gpuBuffer.close()
	}

	private data class TextVertex(
		val x: Float, val y: Float, val z: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int
	)

	private fun buildTextVertices(
		atlas: SDFFontAtlas,
		text: String,
		startX: Float,
		startY: Float,
		color: Color
	): List<TextVertex> {
		val vertices = mutableListOf<TextVertex>()
		var penX = startX
		var charCount = 0

		for (char in text) {
			val glyph = atlas.getGlyph(char.code)
			if (glyph == null) continue
			charCount++

			val x0 = penX + glyph.bearingX
			val y0 = startY - glyph.bearingY
			val x1 = x0 + glyph.width / atlas.baseSize
			val y1 = y0 + glyph.height / atlas.baseSize

			// Quad vertices (counter-clockwise for MC)
			// Bottom-left
			vertices.add(TextVertex(x0, y1, 0f, glyph.u0, glyph.v1, color.red, color.green, color.blue, color.alpha))
			// Bottom-right
			vertices.add(TextVertex(x1, y1, 0f, glyph.u1, glyph.v1, color.red, color.green, color.blue, color.alpha))
			// Top-right
			vertices.add(TextVertex(x1, y0, 0f, glyph.u1, glyph.v0, color.red, color.green, color.blue, color.alpha))
			// Top-left
			vertices.add(TextVertex(x0, y0, 0f, glyph.u0, glyph.v0, color.red, color.green, color.blue, color.alpha))

			penX += glyph.advance
		}

		return vertices
	}

	private fun uploadTextVertices(vertices: List<TextVertex>): GpuBuffer? {
		if (vertices.isEmpty()) return null

		var result: GpuBuffer? = null
		BufferAllocator(vertices.size * 24).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				VertexFormats.POSITION_TEXTURE_COLOR
			)

			for (v in vertices) {
				builder.vertex(v.x, v.y, v.z)
					.texture(v.u, v.v)
					.color(v.r, v.g, v.b, v.a)
			}

			builder.endNullable()?.let { built ->
				result = RenderSystem.getDevice().createBuffer(
					{ "SDF Text Buffer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				built.close()
			}
		}

		return result
	}

	/** Calculate text width in world units. */
	fun getWidth(font: SDFFontAtlas? = null, text: String, fontSize: Float = 1f): Float {
		val atlas = font ?: getDefaultFont()
		return atlas.getStringWidth(text, fontSize)
	}

	/** Get line height for a font at given size. */
	fun getLineHeight(font: SDFFontAtlas? = null, fontSize: Float = 1f): Float {
		val atlas = font ?: getDefaultFont()
		return atlas.lineHeight * fontSize / atlas.baseSize
	}

	/** Clean up all loaded fonts. */
	fun cleanup() {
		fonts.values.forEach { it.close() }
		fonts.clear()
		defaultFont = null
	}
}