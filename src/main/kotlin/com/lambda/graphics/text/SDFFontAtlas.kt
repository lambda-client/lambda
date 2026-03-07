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
import com.lambda.util.stream
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gl.GpuSampler
import net.minecraft.client.texture.NativeImage
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTVertex
import org.lwjgl.stb.STBTruetype.STBTT_vcurve
import org.lwjgl.stb.STBTruetype.STBTT_vline
import org.lwjgl.stb.STBTruetype.STBTT_vmove
import org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex
import org.lwjgl.stb.STBTruetype.stbtt_FreeShape
import org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphBitmapBox
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphBox
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphHMetrics
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphShape
import org.lwjgl.stb.STBTruetype.stbtt_InitFont
import org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SDFFontAtlas(
	fontPath: String,
	val baseSize: Float = 256f,
	val sdfSpread: Int = 16,
	val atlasSize: Int = 4096
) : AutoCloseable {
	data class Glyph(
		val codepoint: Int,
		val width: Int,
		val height: Int,
		val bearingX: Float,
		val bearingY: Float,
		val advance: Float,
		val u0: Float, val v0: Float,
		val u1: Float, val v1: Float
	)

	private data class GlyphJob(
		val codepoint: Int,
		val glyphIndex: Int,
		val atlasX: Int,
		val atlasY: Int,
		val paddedW: Int,
		val paddedH: Int,
		val glyphW: Int,
		val glyphH: Int,
		val glyph: Glyph
	)

	private val fontBuffer: ByteBuffer
	private val fontInfo: STBTTFontinfo
	private var atlasData: ByteArray? = null
	private val glyphs = mutableMapOf<Int, Glyph>()

	private var glTexture: GpuTexture? = null
	private var glTextureView: GpuTextureView? = null
	private var gpuSampler: GpuSampler? = null

	val lineHeight: Float
	val ascent: Float
	val descent: Float
	val scale: Float

	val sdfPixelRange: Float get() = (sdfSpread * 2).toFloat()

	val textureView: GpuTextureView? get() = glTextureView

	val sampler: GpuSampler? get() = gpuSampler

	val isUploaded: Boolean get() = glTexture != null

	init {
		val fontBytes = fontPath.stream.readAllBytes()
		fontBuffer = MemoryUtil.memAlloc(fontBytes.size).put(fontBytes).flip()

		fontInfo = STBTTFontinfo.create()
		if (!stbtt_InitFont(fontInfo, fontBuffer)) {
			MemoryUtil.memFree(fontBuffer)
			throw RuntimeException("Failed to initialize font: $fontPath")
		}

		scale = stbtt_ScaleForPixelHeight(fontInfo, baseSize)

		MemoryStack.stackPush().use { stack ->
			val ascentBuf = stack.mallocInt(1)
			val descentBuf = stack.mallocInt(1)
			val lineGapBuf = stack.mallocInt(1)
			stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf)

			ascent = ascentBuf[0] * scale
			descent = descentBuf[0] * scale
			lineHeight = (ascentBuf[0] - descentBuf[0] + lineGapBuf[0]) * scale
		}

		atlasData = ByteArray(atlasSize * atlasSize)
		buildSDFAtlas()
	}

	private fun buildSDFAtlas() {
		val data = atlasData ?: return
		var penX = sdfSpread
		var penY = sdfSpread
		var rowHeight = 0

		val codepoints = (32..126) + (160..255)
		val jobs = mutableListOf<GlyphJob>()

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
				val paddedW = glyphW + sdfSpread * 2
				val paddedH = glyphH + sdfSpread * 2

				if (penX + paddedW >= atlasSize) {
					penX = sdfSpread
					penY += rowHeight + sdfSpread
					rowHeight = 0
				}

				if (penY + paddedH >= atlasSize) {
					System.err.println("SDF Atlas overflow at codepoint $cp")
					break
				}

				val glyph = Glyph(
					codepoint = cp,
					width = paddedW,
					height = paddedH,
					bearingX = (x0[0] - sdfSpread) / baseSize,
					bearingY = (-y0[0] + sdfSpread) / baseSize,
					advance = advanceWidth[0] * scale / baseSize,
					u0 = penX.toFloat() / atlasSize,
					v0 = penY.toFloat() / atlasSize,
					u1 = (penX + paddedW).toFloat() / atlasSize,
					v1 = (penY + paddedH).toFloat() / atlasSize
				)

				glyphs[cp] = glyph

				if (glyphW > 0 && glyphH > 0) {
					jobs.add(GlyphJob(
						codepoint = cp,
						glyphIndex = glyphIndex,
						atlasX = penX,
						atlasY = penY,
						paddedW = paddedW,
						paddedH = paddedH,
						glyphW = glyphW,
						glyphH = glyphH,
						glyph = glyph
					))
				}

				penX += paddedW + sdfSpread
				rowHeight = maxOf(rowHeight, paddedH)
			}
		}

		runBlocking(Dispatchers.Default) {
			for (job in jobs) {
				launch {
					generateGlyphSDF(
						job.glyphIndex, data,
						job.atlasX, job.atlasY,
						job.paddedW, job.paddedH,
						job.glyphW, job.glyphH
					)
				}
			}
		}
	}

	private fun generateGlyphSDF(
		glyphIndex: Int,
		atlasData: ByteArray,
		atlasX: Int, atlasY: Int,
		paddedW: Int, paddedH: Int,
		glyphW: Int, glyphH: Int
	) {
		MemoryStack.stackPush().use { stack ->
			val boxX0 = stack.mallocInt(1)
			val boxY0 = stack.mallocInt(1)
			val boxX1 = stack.mallocInt(1)
			val boxY1 = stack.mallocInt(1)
			stbtt_GetGlyphBox(fontInfo, glyphIndex, boxX0, boxY0, boxX1, boxY1)
			
			val fontX0 = boxX0[0].toFloat()
			val fontY0 = boxY0[0].toFloat()
			val fontX1 = boxX1[0].toFloat()
			val fontY1 = boxY1[0].toFloat()
			val fontWidth = fontX1 - fontX0
			val fontHeight = fontY1 - fontY0

			val verticesPtr = stack.mallocPointer(1)
			val numVertices = stbtt_GetGlyphShape(fontInfo, glyphIndex, verticesPtr)
			
			if (numVertices <= 0 || fontWidth <= 0 || fontHeight <= 0) {
				for (py in 0 until paddedH) {
					for (px in 0 until paddedW) {
						val index = (atlasY + py) * atlasSize + atlasX + px
						if (index >= 0 && index < atlasSize * atlasSize) {
							atlasData[index] = 0
						}
					}
				}
				return
			}
			
			val vertices = STBTTVertex.create(verticesPtr[0], numVertices)
			
			try {
				val segments = mutableListOf<CurveSegment>()
				var lastX = 0f
				var lastY = 0f
				
				for (i in 0 until numVertices) {
					val v = vertices[i]
					val type = v.type().toInt()
					val x = v.x().toFloat()
					val y = v.y().toFloat()
					
					when (type) {
						STBTT_vmove.toInt() -> {
							lastX = x
							lastY = y
						}
						STBTT_vline.toInt() -> {
							segments.add(LineSegment(lastX, lastY, x, y))
							lastX = x
							lastY = y
						}
						STBTT_vcurve.toInt() -> {
							val cx = v.cx().toFloat()
							val cy = v.cy().toFloat()
							segments.add(QuadraticBezier(lastX, lastY, cx, cy, x, y))
							lastX = x
							lastY = y
						}
					}
				}

				val fontUnitsPerPixelX = fontWidth / glyphW
				val fontUnitsPerPixelY = fontHeight / glyphH

				for (py in 0 until paddedH) {
					for (px in 0 until paddedW) {
						val gx = px - sdfSpread
						val gy = py - sdfSpread

						val fontX = fontX0 + gx * fontUnitsPerPixelX
						val fontY = fontY1 - gy * fontUnitsPerPixelY

						var minDist = Float.MAX_VALUE
						for (seg in segments) {
							val d = seg.distance(fontX, fontY)
							if (d < minDist) {
								minDist = d
							}
						}

						val inside = computeWindingNumber(fontX, fontY, segments) != 0
						val signedDist = if (inside) minDist else -minDist

						val avgFontUnitsPerPixel = (fontUnitsPerPixelX + fontUnitsPerPixelY) / 2f
						val pixelDist = signedDist / avgFontUnitsPerPixel

						val normalizedDist = (pixelDist / sdfSpread + 1f) * 0.5f
						val value = (normalizedDist.coerceIn(0f, 1f) * 255).toInt().toByte()
						
						val index = (atlasY + py) * atlasSize + atlasX + px
						if (index >= 0 && index < atlasSize * atlasSize) {
							atlasData[index] = value
						}
					}
				}
			} finally {
				stbtt_FreeShape(fontInfo, vertices)
			}
		}
	}

	private sealed interface CurveSegment {
		fun distance(px: Float, py: Float): Float
	}

	private data class LineSegment(
		val x0: Float, val y0: Float,
		val x1: Float, val y1: Float
	) : CurveSegment {
		override fun distance(px: Float, py: Float): Float {
			val dx = x1 - x0
			val dy = y1 - y0
			val lenSq = dx * dx + dy * dy
			if (lenSq < 1e-10f) return sqrt((px - x0) * (px - x0) + (py - y0) * (py - y0))
			
			val t = ((px - x0) * dx + (py - y0) * dy) / lenSq
			val tc = t.coerceIn(0f, 1f)
			val nearX = x0 + tc * dx
			val nearY = y0 + tc * dy
			return sqrt((px - nearX) * (px - nearX) + (py - nearY) * (py - nearY))
		}
	}

	private data class QuadraticBezier(
		val x0: Float, val y0: Float,
		val cx: Float, val cy: Float,
		val x1: Float, val y1: Float
	) : CurveSegment {
		override fun distance(px: Float, py: Float): Float {
			var bestT = 0f
			var minDist = Float.MAX_VALUE

			for (i in 0..32) {
				val t = i / 32f
				val d = distAtT(px, py, t)
				if (d < minDist) {
					minDist = d
					bestT = t
				}
			}

			val step = 1f / 64f
			val tLo = (bestT - step * 2).coerceIn(0f, 1f)
			val tHi = (bestT + step * 2).coerceIn(0f, 1f)
			
			for (i in 0..16) {
				val t = tLo + (tHi - tLo) * i / 16f
				val d = distAtT(px, py, t)
				if (d < minDist) {
					minDist = d
				}
			}
			
			return minDist
		}
		
		private fun distAtT(px: Float, py: Float, t: Float): Float {
			val u = 1f - t
			val bx = u * u * x0 + 2 * u * t * cx + t * t * x1
			val by = u * u * y0 + 2 * u * t * cy + t * t * y1
			return sqrt((px - bx) * (px - bx) + (py - by) * (py - by))
		}

		fun getSubdividedPoints(numSegments: Int = 8): List<Pair<Float, Float>> {
			val points = mutableListOf<Pair<Float, Float>>()
			for (i in 0..numSegments) {
				val t = i.toFloat() / numSegments
				val u = 1f - t
				val bx = u * u * x0 + 2 * u * t * cx + t * t * x1
				val by = u * u * y0 + 2 * u * t * cy + t * t * y1
				points.add(Pair(bx, by))
			}
			return points
		}
	}

	private fun computeWindingNumber(px: Float, py: Float, segments: List<CurveSegment>): Int {
		var winding = 0
		for (seg in segments) {
			when (seg) {
				is LineSegment -> {
					winding += windingForLine(px, py, seg.x0, seg.y0, seg.x1, seg.y1)
				}
				is QuadraticBezier -> {
					val points = seg.getSubdividedPoints(8)
					for (i in 0 until points.size - 1) {
						val (ax, ay) = points[i]
						val (bx, by) = points[i + 1]
						winding += windingForLine(px, py, ax, ay, bx, by)
					}
				}
			}
		}
		return winding
	}

	private fun windingForLine(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Int {
		if (y0 <= py) {
			if (y1 > py) {
				val cross = (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0)
				if (cross > 0) return 1
			}
		} else {
			if (y1 <= py) {
				val cross = (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0)
				if (cross < 0) return -1
			}
		}
		return 0
	}

	fun upload() {
		if (glTexture != null) return
		val data = atlasData ?: return

		RenderSystem.assertOnRenderThread()

		val gpuDevice = RenderSystem.getDevice()

		glTexture = gpuDevice.createTexture(
			"Lambda SDF FontAtlas",
			5,
			TextureFormat.RGBA8,
			atlasSize, atlasSize,
			1, 1
		)

		glTextureView = gpuDevice.createTextureView(glTexture)

		gpuSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)

		val nativeImage = NativeImage(atlasSize, atlasSize, false)
		for (y in 0 until atlasSize) {
			for (x in 0 until atlasSize) {
				val sdfValue = data[y * atlasSize + x].toInt() and 0xFF
				val abgr = (sdfValue shl 24) or 0x00FFFFFF
				nativeImage.setColor(x, y, abgr)
			}
		}

		RenderSystem.getDevice().createCommandEncoder().writeToTexture(glTexture, nativeImage)
		nativeImage.close()

		atlasData = null
	}

	fun getGlyph(codepoint: Int): Glyph? = glyphs[codepoint]

	private fun getStringWidth(text: String, fontSize: Float): Float {
		var width = 0f
		for (char in text) {
			val glyph = glyphs[char.code] ?: glyphs[' '.code] ?: continue
			width += glyph.advance * fontSize
		}
		return width
	}

	private val screenWidth: Float
		get() = mc.window.scaledWidth.toFloat()

	private val screenHeight: Float
		get() = mc.window.scaledHeight.toFloat()

	fun getStringWidthNormalized(text: String, normalizedSize: Float): Float {
		val targetPixelHeight = normalizedSize * screenHeight
		val pixelSize = targetPixelHeight * baseSize / ascent
		val pixelWidth = getStringWidth(text, pixelSize)
		return pixelWidth / screenWidth
	}

	fun getDescentNormalized(normalizedSize: Float): Float {
		return normalizedSize * descent / ascent
	}

	fun getStringDimensionsNormalized(text: String, normalizedSize: Float): Pair<Float, Float> {
		return Pair(getStringWidthNormalized(text, normalizedSize), normalizedSize)
	}

	fun getSizeForWidthNormalized(text: String, targetWidthNormalized: Float): Float {
		var rawAdvance = 0f
		for (char in text) {
			val glyph = glyphs[char.code] ?: glyphs[' '.code] ?: continue
			rawAdvance += glyph.advance
		}
		if (rawAdvance <= 0f) return 0f

		return (targetWidthNormalized * screenWidth * ascent) / (rawAdvance * screenHeight * baseSize)
	}

	override fun close() {
		glTextureView?.close()
		glTextureView = null
		glTexture?.close()
		glTexture = null
		gpuSampler = null
		atlasData = null
		MemoryUtil.memFree(fontBuffer)
	}
}