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

import com.lambda.util.stream
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gl.GpuSampler
import net.minecraft.client.texture.NativeImage
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex
import org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphBitmapBox
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphHMetrics
import org.lwjgl.stb.STBTruetype.stbtt_InitFont
import org.lwjgl.stb.STBTruetype.stbtt_MakeGlyphBitmap
import org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * Font atlas that uses MC 1.21's GPU texture APIs for proper rendering.
 * 
 * Uses STB TrueType for glyph rasterization and MC's GpuTexture/GpuTextureView/GpuSampler
 * for texture management, enabling correct texture binding via RenderPass.bindTexture().
 *
 * @param fontPath Resource path to TTF/OTF file
 * @param fontSize Font size in pixels
 * @param atlasWidth Atlas texture width (must be power of 2)
 * @param atlasHeight Atlas texture height (must be power of 2)
 */
class FontAtlas(
	fontPath: String,
	val fontSize: Float = 64f,
	val atlasWidth: Int = 2048,
	val atlasHeight: Int = 2048
) : AutoCloseable {

	data class Glyph(
		val codepoint: Int,
		val x0: Int, val y0: Int,
		val x1: Int, val y1: Int,
		val xOffset: Float, val yOffset: Float,
		val xAdvance: Float,
		val u0: Float, val v0: Float,
		val u1: Float, val v1: Float
	)

	private val fontBuffer: ByteBuffer
	private val fontInfo: STBTTFontinfo
	private val glyphs = mutableMapOf<Int, Glyph>()
	
	// MC 1.21 GPU texture objects
	private var glTexture: GpuTexture? = null
	private var glTextureView: GpuTextureView? = null
	private var gpuSampler: GpuSampler? = null
	
	// Temporary storage for atlas during construction
	private var atlasData: ByteArray? = null

	val lineHeight: Float
	val ascent: Float
	val descent: Float

	/** Get the texture view for binding in render pass */
	val textureView: GpuTextureView?
		get() = glTextureView
	
	/** Get the sampler for binding in render pass */
	val sampler: GpuSampler?
		get() = gpuSampler
	
	/** Check if texture is uploaded and ready */
	val isUploaded: Boolean
		get() = glTexture != null

	init {
		// Load font file
		val fontBytes = fontPath.stream.readAllBytes()
		fontBuffer = MemoryUtil.memAlloc(fontBytes.size).put(fontBytes).flip()

		fontInfo = STBTTFontinfo.create()
		if (!stbtt_InitFont(fontInfo, fontBuffer)) {
			MemoryUtil.memFree(fontBuffer)
			throw RuntimeException("Failed to initialize font: $fontPath")
		}

		// Calculate scale and metrics
		val scale = stbtt_ScaleForPixelHeight(fontInfo, fontSize)

		MemoryStack.stackPush().use { stack ->
			val ascentBuf = stack.mallocInt(1)
			val descentBuf = stack.mallocInt(1)
			val lineGapBuf = stack.mallocInt(1)
			stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf)

			ascent = ascentBuf[0] * scale
			descent = descentBuf[0] * scale
			lineHeight = (ascentBuf[0] - descentBuf[0] + lineGapBuf[0]) * scale
		}

		// Build atlas data
		atlasData = ByteArray(atlasWidth * atlasHeight * 4) // RGBA
		buildAtlas(scale)
	}

	private fun buildAtlas(scale: Float) {
		val data = atlasData ?: return
		var penX = 1
		var penY = 1
		var rowHeight = 0

		// Rasterize printable ASCII + extended Latin
		val codepoints = (32..126) + (160..255)

		MemoryStack.stackPush().use { stack ->
			val x0 = stack.mallocInt(1)
			val y0 = stack.mallocInt(1)
			val x1 = stack.mallocInt(1)
			val y1 = stack.mallocInt(1)
			val advanceWidth = stack.mallocInt(1)
			val leftSideBearing = stack.mallocInt(1)

			for (cp in codepoints) {
				val glyphIndex = stbtt_FindGlyphIndex(fontInfo, cp)
				if (glyphIndex == 0 && cp != 32) continue

				stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advanceWidth, leftSideBearing)
				stbtt_GetGlyphBitmapBox(fontInfo, glyphIndex, scale, scale, x0, y0, x1, y1)

				val glyphW = x1[0] - x0[0]
				val glyphH = y1[0] - y0[0]

				// Check if we need to wrap to next row
				if (penX + glyphW + 1 >= atlasWidth) {
					penX = 1
					penY += rowHeight + 1
					rowHeight = 0
				}

				// Check atlas overflow
				if (penY + glyphH + 1 >= atlasHeight) break

				// Rasterize glyph
				if (glyphW > 0 && glyphH > 0) {
					val tempBuffer = MemoryUtil.memAlloc(glyphW * glyphH)
					try {
						stbtt_MakeGlyphBitmap(
							fontInfo, tempBuffer,
							glyphW, glyphH, glyphW, scale, scale, glyphIndex
						)
						// Copy to atlas as RGBA (white with grayscale as alpha)
						for (row in 0 until glyphH) {
							for (col in 0 until glyphW) {
								val srcIndex = row * glyphW + col
								val alpha = tempBuffer.get(srcIndex).toInt() and 0xFF
								val dstIndex = ((penY + row) * atlasWidth + penX + col) * 4
								data[dstIndex + 0] = 0xFF.toByte() // R
								data[dstIndex + 1] = 0xFF.toByte() // G
								data[dstIndex + 2] = 0xFF.toByte() // B
								data[dstIndex + 3] = alpha.toByte() // A
							}
						}
					} finally {
						MemoryUtil.memFree(tempBuffer)
					}
				}

				// Store glyph info
				glyphs[cp] = Glyph(
					codepoint = cp,
					x0 = penX, y0 = penY,
					x1 = penX + glyphW, y1 = penY + glyphH,
					xOffset = x0[0].toFloat(),
					yOffset = y0[0].toFloat(),
					xAdvance = advanceWidth[0] * scale,
					u0 = penX.toFloat() / atlasWidth,
					v0 = penY.toFloat() / atlasHeight,
					u1 = (penX + glyphW).toFloat() / atlasWidth,
					v1 = (penY + glyphH).toFloat() / atlasHeight
				)

				penX += glyphW + 1
				rowHeight = maxOf(rowHeight, glyphH)
			}
		}
	}

	/**
	 * Upload atlas to GPU using MC 1.21 APIs.
	 * Must be called on the render thread.
	 */
	fun upload() {
		if (glTexture != null) return // Already uploaded
		val data = atlasData ?: return

		RenderSystem.assertOnRenderThread()

		val gpuDevice = RenderSystem.getDevice()

		// Create GPU texture (usage flags: 5 = COPY_DST | TEXTURE_BINDING)
		glTexture = gpuDevice.createTexture(
			"Lambda FontAtlas",
			5, // COPY_DST (1) | TEXTURE_BINDING (4)
			TextureFormat.RGBA8,
			atlasWidth, atlasHeight,
			1, // layers
			1  // mip levels
		)

		// Create texture view
		glTextureView = gpuDevice.createTextureView(glTexture)

		// Get sampler with linear filtering
		gpuSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)

		// Create NativeImage and copy data
		val nativeImage = NativeImage(atlasWidth, atlasHeight, false)
		for (y in 0 until atlasHeight) {
			for (x in 0 until atlasWidth) {
				val srcIndex = (y * atlasWidth + x) * 4
				val r = data[srcIndex + 0].toInt() and 0xFF
				val g = data[srcIndex + 1].toInt() and 0xFF
				val b = data[srcIndex + 2].toInt() and 0xFF
				val a = data[srcIndex + 3].toInt() and 0xFF
				// NativeImage uses ABGR format
				val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
				nativeImage.setColor(x, y, abgr)
			}
		}

		// Upload to GPU
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(glTexture, nativeImage)
		nativeImage.close()

		// Free atlas data after upload
		atlasData = null
	}

	fun getGlyph(codepoint: Int): Glyph? = glyphs[codepoint]

	/** Calculate the width of a string in pixels. */
	fun getStringWidth(text: String): Float {
		var width = 0f
		for (char in text) {
			val glyph = glyphs[char.code] ?: glyphs[' '.code] ?: continue
			width += glyph.xAdvance
		}
		return width
	}

	override fun close() {
		glTextureView?.close()
		glTextureView = null
		glTexture?.close()
		glTexture = null
		gpuSampler = null // Sampler is managed by cache, don't close
		atlasData = null
		MemoryUtil.memFree(fontBuffer)
	}
}
