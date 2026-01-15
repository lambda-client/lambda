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

/**
 * Text renderer using MC 1.21's proper GPU texture APIs.
 * 
 * Uses FontAtlas for glyph data and binds textures correctly via
 * RenderPass.bindTexture() for compatibility with MC's new rendering pipeline.
 */
class TextRenderer(
	fontPath: String,
	fontSize: Float = 256f,
	atlasSize: Int = 512
) : AutoCloseable {

	private val atlas = FontAtlas(fontPath, fontSize, atlasSize, atlasSize)

	/** Font line height in pixels */
	val lineHeight: Float get() = atlas.lineHeight

	/** Font ascent in pixels */
	val ascent: Float get() = atlas.ascent

	/** Font descent in pixels (negative value) */
	val descent: Float get() = atlas.descent

	/**
	 * Draw text in world space, facing the camera (billboard style).
	 *
	 * @param pos World position for the text
	 * @param text Text string to render
	 * @param color Text color
	 * @param scale World-space scale (0.025f is similar to MC name tags)
	 * @param centered Center text horizontally at position
	 * @param seeThrough Render through walls
	 */
	fun drawWorld(
		pos: Vec3d,
		text: String,
		color: Color = Color.WHITE,
		scale: Float = 0.025f,
		centered: Boolean = true,
		seeThrough: Boolean = false
	) {
		val camera = mc.gameRenderer?.camera ?: return
		if (!atlas.isUploaded) atlas.upload()
		val textureView = atlas.textureView ?: return
		val sampler = atlas.sampler ?: return

		val cameraPos = camera.pos

		// Build transformation matrix: translate, billboard, scale
		val modelView = Matrix4f(com.lambda.graphics.RenderMain.modelViewMatrix)
		modelView.translate(
			(pos.x - cameraPos.x).toFloat(),
			(pos.y - cameraPos.y).toFloat(),
			(pos.z - cameraPos.z).toFloat()
		)
		// Billboard - rotate to face camera
		modelView.rotate(camera.rotation)
		// Scale with negative Y to flip text vertically (MC convention)
		modelView.scale(scale, -scale, scale)

		// Calculate text offset for centering
		val textWidth = atlas.getStringWidth(text)
		val xOffset = if (centered) -textWidth / 2f else 0f

		// Build and upload vertices
		val (buffer, vertexCount) = buildAndUploadVertices(text, xOffset, 0f, color) ?: return

		try {
			// Use TEXT_QUADS pipeline
			val pipeline = if (seeThrough) LambdaRenderPipelines.TEXT_QUADS_THROUGH
			else LambdaRenderPipelines.TEXT_QUADS

			// Create dynamic transform
			val dynamicTransform = RenderSystem.getDynamicUniforms()
				.write(
					modelView,
					Vector4f(1f, 1f, 1f, 1f),
					Vector3f(0f, 0f, 0f),
					Matrix4f()
				)

			// Create render pass and draw
			RegionRenderer.createRenderPass("TextRenderer World", !seeThrough)?.use { pass ->
				pass.setPipeline(pipeline)
				RenderSystem.bindDefaultUniforms(pass)
				
				// Bind our texture using MC 1.21's proper API
				pass.bindTexture("Sampler0", textureView, sampler)
				
				// Set transform
				pass.setUniform("DynamicTransforms", dynamicTransform)
				
				// Set vertex buffer and draw
				pass.setVertexBuffer(0, buffer)
				val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
				val indexBuffer = shapeIndexBuffer.getIndexBuffer(vertexCount)
				pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
				pass.drawIndexed(0, 0, vertexCount, 1)
			}
		} finally {
			buffer.close()
		}
	}

	/**
	 * Draw text in screen space (2D overlay).
	 *
	 * @param x Screen X position
	 * @param y Screen Y position  
	 * @param text Text string to render
	 * @param color Text color
	 * @param scale Scale factor (1.0 = native font size)
	 */
	fun drawScreen(
		x: Float,
		y: Float,
		text: String,
		color: Color = Color.WHITE,
		scale: Float = 1f
	) {
		if (!atlas.isUploaded) atlas.upload()
		val textureView = atlas.textureView ?: return
		val sampler = atlas.sampler ?: return

		// Build transformation for screen space with orthographic projection
		val window = mc.window
		val ortho = Matrix4f().ortho(
			0f, window.scaledWidth.toFloat(),
			window.scaledHeight.toFloat(), 0f,
			-1000f, 1000f
		)
		
		val modelView = Matrix4f()
		modelView.translate(x, y, 0f)
		modelView.scale(scale, scale, 1f)
		
		val mvp = Matrix4f(ortho).mul(modelView)

		// Build and upload vertices
		val (buffer, vertexCount) = buildAndUploadVertices(text, 0f, 0f, color) ?: return

		try {
			val pipeline = LambdaRenderPipelines.TEXT_QUADS_THROUGH // No depth test for screen

			// Create dynamic transform
			val dynamicTransform = RenderSystem.getDynamicUniforms()
				.write(
					mvp,
					Vector4f(1f, 1f, 1f, 1f),
					Vector3f(0f, 0f, 0f),
					Matrix4f()
				)

			RegionRenderer.createRenderPass("TextRenderer Screen", false)?.use { pass ->
				pass.setPipeline(pipeline)
				// Note: not calling bindDefaultUniforms - we provide complete MVP in DynamicTransforms
				pass.bindTexture("Sampler0", textureView, sampler)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				
				pass.setVertexBuffer(0, buffer)
				val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
				val indexBuffer = shapeIndexBuffer.getIndexBuffer(vertexCount)
				pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
				pass.drawIndexed(0, 0, vertexCount, 1)
			}
		} finally {
			buffer.close()
		}
	}

	/**
	 * Get the width of a text string in pixels at scale 1.0.
	 */
	fun getStringWidth(text: String): Float = atlas.getStringWidth(text)

	/**
	 * Build and upload vertices to GPU buffer.
	 * Returns the buffer and vertex count, or null if no vertices.
	 */
	private fun buildAndUploadVertices(
		text: String,
		startX: Float,
		startY: Float,
		color: Color
	): Pair<GpuBuffer, Int>? {
		val penY = startY + atlas.ascent
		var penX = startX
		
		// Count quads for allocation
		var quadCount = 0
		for (char in text) {
			if (atlas.getGlyph(char.code) != null || atlas.getGlyph(' '.code) != null) {
				quadCount++
			}
		}
		if (quadCount == 0) return null
		
		val vertexCount = quadCount * 4
		val vertexSize = VertexFormats.POSITION_TEXTURE_COLOR.vertexSize
		
		var result: Pair<GpuBuffer, Int>? = null
		BufferAllocator(vertexCount * vertexSize).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				VertexFormats.POSITION_TEXTURE_COLOR
			)
			
			val r = color.red
			val g = color.green
			val b = color.blue
			val a = color.alpha
			
			for (char in text) {
				val glyph = atlas.getGlyph(char.code) ?: atlas.getGlyph(' '.code) ?: continue
				
				val x0 = penX + glyph.xOffset
				val y0 = penY + glyph.yOffset
				val x1 = x0 + (glyph.x1 - glyph.x0)
				val y1 = y0 + (glyph.y1 - glyph.y0)
				
				// Bottom-left
				builder.vertex(x0, y1, 0f).texture(glyph.u0, glyph.v1).color(r, g, b, a)
				// Bottom-right
				builder.vertex(x1, y1, 0f).texture(glyph.u1, glyph.v1).color(r, g, b, a)
				// Top-right
				builder.vertex(x1, y0, 0f).texture(glyph.u1, glyph.v0).color(r, g, b, a)
				// Top-left
				builder.vertex(x0, y0, 0f).texture(glyph.u0, glyph.v0).color(r, g, b, a)
				
				penX += glyph.xAdvance
			}
			
			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer = gpuDevice.createBuffer(
					{ "Lambda TextRenderer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				result = buffer to built.drawParameters.indexCount()
				built.close()
			}
		}
		
		return result
	}

	override fun close() {
		atlas.close()
	}

	companion object {
		private val loadedFonts = mutableMapOf<String, TextRenderer>()

		/**
		 * Load or get a cached font renderer.
		 */
		fun loadFont(fontPath: String, fontSize: Float = 16f): TextRenderer {
			val key = "$fontPath:$fontSize"
			return loadedFonts.getOrPut(key) {
				TextRenderer(fontPath, fontSize)
			}
		}

		/**
		 * Close and clear all cached fonts.
		 */
		fun closeAll() {
			loadedFonts.values.forEach { it.close() }
			loadedFonts.clear()
		}
	}
}
