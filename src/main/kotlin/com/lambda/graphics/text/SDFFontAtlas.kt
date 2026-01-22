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

/**
 * Signed Distance Field font atlas for high-quality scalable text rendering.
 *
 * SDF fonts store the distance to the nearest edge instead of raw coverage,
 * enabling crisp text at any scale with effects like outlines and glows.
 * 
 * Uses MC 1.21's GpuTexture APIs for proper texture binding via RenderPass.bindTexture().
 *
 * @param fontPath Resource path to TTF/OTF file
 * @param baseSize Base font size for SDF generation (larger = more detail, 48-64 recommended)
 * @param sdfSpread SDF spread in pixels (how far the distance field extends)
 * @param atlasSize Atlas texture dimensions (must be power of 2)
 */
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

	private val fontBuffer: ByteBuffer
	private val fontInfo: STBTTFontinfo
	private var atlasData: ByteArray? = null
	private val glyphs = mutableMapOf<Int, Glyph>()

	// MC 1.21 GPU texture objects
	private var glTexture: GpuTexture? = null
	private var glTextureView: GpuTextureView? = null
	private var gpuSampler: GpuSampler? = null

	val lineHeight: Float
	val ascent: Float
	val descent: Float
	val scale: Float

	/** The pixel range used for SDF, needed by shader for proper AA */
	val sdfPixelRange: Float get() = (sdfSpread * 2).toFloat()

	/** Get the texture view for binding in render pass */
	val textureView: GpuTextureView? get() = glTextureView

	/** Get the sampler for binding in render pass */
	val sampler: GpuSampler? get() = gpuSampler

	/** Check if texture is uploaded and ready */
	val isUploaded: Boolean get() = glTexture != null

	init {
		// Load font file
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

				if (glyphW > 0 && glyphH > 0) {
					generateGlyphSDF(glyphIndex, data, penX, penY, paddedW, paddedH, glyphW, glyphH)
				}

				glyphs[cp] = Glyph(
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

				penX += paddedW + sdfSpread
				rowHeight = maxOf(rowHeight, paddedH)
			}
		}
	}

	/**
	 * Generate vector-based SDF for a glyph.
	 * Computes distances directly from bezier curves for smooth edges.
	 */
	private fun generateGlyphSDF(
		glyphIndex: Int,
		atlasData: ByteArray,
		atlasX: Int, atlasY: Int,
		paddedW: Int, paddedH: Int,
		glyphW: Int, glyphH: Int
	) {
		MemoryStack.stackPush().use { stack ->
			// Get glyph bounding box in FONT UNITS
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
			
			// Get glyph shape (bezier curves in font units)
			val verticesPtr = stack.mallocPointer(1)
			val numVertices = stbtt_GetGlyphShape(fontInfo, glyphIndex, verticesPtr)
			
			if (numVertices <= 0 || fontWidth <= 0 || fontHeight <= 0) {
				// Empty glyph (space, etc) - fill with "outside" value
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
				// Extract curve segments from vertices (in font units)
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
				
				// Font units per pixel in the output
				// The glyph area (without padding) maps to the font bounding box
				val fontUnitsPerPixelX = fontWidth / glyphW
				val fontUnitsPerPixelY = fontHeight / glyphH
				
				// Compute SDF for each pixel in output
				for (py in 0 until paddedH) {
					for (px in 0 until paddedW) {
						// Map output pixel to font units
						// px, py are in padded coordinate space
						// The glyph occupies pixels [sdfSpread, sdfSpread+glyphW) x [sdfSpread, sdfSpread+glyphH)
						val gx = px - sdfSpread  // Glyph-local X (0 to glyphW maps to fontX0 to fontX1)
						val gy = py - sdfSpread  // Glyph-local Y
						
						// Convert to font units
						// X: direct mapping
						val fontX = fontX0 + gx * fontUnitsPerPixelX
						// Y: font coords have Y up, screen coords have Y down
						// gy=0 should map to fontY1 (top), gy=glyphH should map to fontY0 (bottom)
						val fontY = fontY1 - gy * fontUnitsPerPixelY
						
						// Find minimum distance to any curve segment (in font units)
						var minDist = Float.MAX_VALUE
						for (seg in segments) {
							val d = seg.distance(fontX, fontY)
							if (d < minDist) {
								minDist = d
							}
						}
						
						// Determine if inside or outside using winding number
						val inside = computeWindingNumber(fontX, fontY, segments) != 0
						val signedDist = if (inside) minDist else -minDist
						
						// Convert distance from font units to pixels
						val avgFontUnitsPerPixel = (fontUnitsPerPixelX + fontUnitsPerPixelY) / 2f
						val pixelDist = signedDist / avgFontUnitsPerPixel
						
						// Normalize: map [-sdfSpread, +sdfSpread] pixels to [0, 1]
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
	
	/** Curve segment interface */
	private sealed interface CurveSegment {
		fun distance(px: Float, py: Float): Float
	}
	
	/** Line segment */
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
	
	/** Quadratic bezier curve */
	private data class QuadraticBezier(
		val x0: Float, val y0: Float,
		val cx: Float, val cy: Float,
		val x1: Float, val y1: Float
	) : CurveSegment {
		override fun distance(px: Float, py: Float): Float {
			// Use iterative refinement for accurate bezier distance
			// First pass: coarse sampling to find approximate t
			var bestT = 0f
			var minDist = Float.MAX_VALUE
			
			// Coarse pass: 32 samples
			for (i in 0..32) {
				val t = i / 32f
				val d = distAtT(px, py, t)
				if (d < minDist) {
					minDist = d
					bestT = t
				}
			}
			
			// Refinement: search around bestT with smaller steps
			val step = 1f / 64f
			var tLo = (bestT - step * 2).coerceIn(0f, 1f)
			var tHi = (bestT + step * 2).coerceIn(0f, 1f)
			
			for (i in 0..16) {
				val t = tLo + (tHi - tLo) * i / 16f
				val d = distAtT(px, py, t)
				if (d < minDist) {
					minDist = d
					bestT = t
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
		
		/** Get subdivided points for winding calculation */
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
	
	/** Compute winding number to determine if point is inside the glyph */
	private fun computeWindingNumber(px: Float, py: Float, segments: List<CurveSegment>): Int {
		var winding = 0
		for (seg in segments) {
			when (seg) {
				is LineSegment -> {
					winding += windingForLine(px, py, seg.x0, seg.y0, seg.x1, seg.y1)
				}
				is QuadraticBezier -> {
					// Subdivide bezier into line segments for accurate winding
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
	
	/** Compute winding contribution for a single line segment */
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

	/**
	 * Compute signed distance field using Euclidean Distance Transform (EDT).
	 * Uses the Felzenszwalb-Huttenlocher algorithm for O(n) linear time.
	 * 
	 * @param coverage Grayscale values 0-1 where > 0.5 is "inside"
	 * @param width Image width
	 * @param height Image height
	 * @return Signed distance field (positive = inside, negative = outside)
	 */
	private fun computeEDT(coverage: FloatArray, width: Int, height: Int): FloatArray {
		val INF = 1e10f
		
		// Create binary inside/outside arrays based on coverage threshold
		val inside = FloatArray(width * height) { i ->
			if (coverage[i] > 0.5f) 0f else INF
		}
		val outside = FloatArray(width * height) { i ->
			if (coverage[i] <= 0.5f) 0f else INF
		}
		
		// Compute EDT for both inside and outside
		edtTransform(inside, width, height)
		edtTransform(outside, width, height)
		
		// Combine into signed distance field
		// distOutside - distInside: positive inside glyph, negative outside
		val sdf = FloatArray(width * height)
		for (i in 0 until width * height) {
			val distInside = sqrt(inside[i])
			val distOutside = sqrt(outside[i])
			sdf[i] = distOutside - distInside
		}
		
		return sdf
	}

	/**
	 * 2D Euclidean Distance Transform using Felzenszwalb-Huttenlocher algorithm.
	 * Transforms the input array in-place to contain squared distances.
	 */
	private fun edtTransform(data: FloatArray, width: Int, height: Int) {
		val INF = 1e10f
		val maxDim = maxOf(width, height)
		
		// Temporary arrays for 1D transform
		val f = FloatArray(maxDim)
		val d = FloatArray(maxDim)
		val v = IntArray(maxDim)
		val z = FloatArray(maxDim + 1)
		
		// Transform columns
		for (x in 0 until width) {
			for (y in 0 until height) {
				f[y] = data[y * width + x]
			}
			edt1d(f, d, v, z, height)
			for (y in 0 until height) {
				data[y * width + x] = d[y]
			}
		}
		
		// Transform rows
		for (y in 0 until height) {
			for (x in 0 until width) {
				f[x] = data[y * width + x]
			}
			edt1d(f, d, v, z, width)
			for (x in 0 until width) {
				data[y * width + x] = d[x]
			}
		}
	}

	/**
	 * 1D squared Euclidean distance transform.
	 * f = input function, d = output distances
	 */
	private fun edt1d(f: FloatArray, d: FloatArray, v: IntArray, z: FloatArray, n: Int) {
		val INF = 1e10f
		var k = 0
		v[0] = 0
		z[0] = -INF
		z[1] = INF
		
		for (q in 1 until n) {
			var s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
			while (s <= z[k]) {
				k--
				s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
			}
			k++
			v[k] = q
			z[k] = s
			z[k + 1] = INF
		}
		
		k = 0
		for (q in 0 until n) {
			while (z[k + 1] < q) {
				k++
			}
			val dist = q - v[k]
			d[q] = dist * dist + f[v[k]]
		}
	}

	/**
	 * Upload atlas to GPU using MC 1.21 APIs.
	 * Must be called on render thread.
	 */
	fun upload() {
		if (glTexture != null) return
		val data = atlasData ?: return

		RenderSystem.assertOnRenderThread()

		val gpuDevice = RenderSystem.getDevice()

		// Create RGBA8 texture - the shader samples red channel for SDF value
		glTexture = gpuDevice.createTexture(
			"Lambda SDF FontAtlas",
			5, // COPY_DST (1) | TEXTURE_BINDING (4)
			TextureFormat.RGBA8,
			atlasSize, atlasSize,
			1, 1
		)

		glTextureView = gpuDevice.createTextureView(glTexture)

		// Use LINEAR filtering for smooth SDF interpolation
		gpuSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)

		// Create NativeImage with SDF value in alpha channel for transparency blending
		// The position_tex_color shader multiplies texture.rgba by vertex color
		// So we need SDF in alpha, with white RGB for the text color from vertex
		val nativeImage = NativeImage(atlasSize, atlasSize, false)
		for (y in 0 until atlasSize) {
			for (x in 0 until atlasSize) {
				val sdfValue = data[y * atlasSize + x].toInt() and 0xFF
				// ABGR format: alpha=sdfValue, blue=255, green=255, red=255
				// SDF in alpha allows proper transparency blending
				val abgr = (sdfValue shl 24) or (255 shl 16) or (255 shl 8) or 255
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

	/** Get screen width in pixels (uses MC's scaled width). */
	private val screenWidth: Float
		get() = mc.window.scaledWidth.toFloat()

	/** Get screen height in pixels (uses MC's scaled height). */
	private val screenHeight: Float
		get() = mc.window.scaledHeight.toFloat()

	/**
	 * Get the width of text using normalized size (0-1 range, matching screenText).
	 * @param text The text string to measure
	 * @param normalizedSize Text size in normalized units (e.g., 0.02 = 2% of screen)
	 * @return Width in normalized units (0-1 range relative to screen width)
	 */
	fun getStringWidthNormalized(text: String, normalizedSize: Float): Float {
		// Apply the same baseSize/ascent correction that screenText uses
		// so dimensions match what actually gets rendered
		val targetPixelHeight = normalizedSize * screenHeight
		val pixelSize = targetPixelHeight * baseSize / ascent
		val pixelWidth = getStringWidth(text, pixelSize)
		return pixelWidth / screenWidth
	}

	/**
	 * Get the descent using normalized size (0-1 range, matching screenText).
	 * @param normalizedSize Text size in normalized units
	 * @return Descent in normalized units (0-1 range relative to screen height)
	 */
	fun getDescentNormalized(normalizedSize: Float): Float {
		// descent / ascent = proportion of ascent that is descent
		return normalizedSize * descent / ascent
	}

	/**
	 * Get both width and height of text using normalized size (0-1 range, matching screenText).
	 * @param text The text string to measure
	 * @param normalizedSize Text size in normalized units
	 * @return Pair of (width, height) in normalized units
	 */
	fun getStringDimensionsNormalized(text: String, normalizedSize: Float): Pair<Float, Float> {
		return Pair(getStringWidthNormalized(text, normalizedSize), normalizedSize)
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