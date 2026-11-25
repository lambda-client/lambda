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

package com.lambda.gui.component

import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lambda.gui.LambdaGui
import com.lambda.gui.core.LocalPresence
import com.lambda.gui.theme.GuiTransition
import com.lambda.gui.theme.Look
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

private val SHADOW_COLOR = Color.Black.copy(alpha = 0.55f)
private val SHADOW_BLUR = 5.dp
private val SHADOW_DROP = 2.dp
private val CLOSE_SHADOW_COLOR = Color.Black.copy(alpha = 0.85f)
private val CLOSE_SHADOW_BLUR = 3.dp
private val CLOSE_SHADOW_DROP = 2.dp

private val DRAWN =
	ImageFilter.makeColorFilter(
		ColorFilter.makeMatrix(
			ColorMatrix(
				0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 0f, 0f,
				0f, 0f, 0f, 64f, 0f,
			)
		),
		null,
		null
	)

private val REPLACE = Paint().apply { blendMode = org.jetbrains.skia.BlendMode.SRC }
private val WITHIN = Paint().apply { blendMode = org.jetbrains.skia.BlendMode.SRC_IN }

private val GUI_SLIDE = 24.dp
private const val GUI_ZOOM = 0.92f

fun Modifier.guiPresence(anchor: TransformOrigin? = null): Modifier =
	composed {
		val presence = LocalPresence.current
		graphicsLayer {
			val shown = presence()
			val styles = LambdaGui.transitions
			if (GuiTransition.Fade in styles) alpha = shown
			if (GuiTransition.Slide in styles && anchor == null) translationY = (shown - 1f) * GUI_SLIDE.toPx()
			if (GuiTransition.Zoom in styles) {
				scaleX = GUI_ZOOM + (1f - GUI_ZOOM) * shown
				scaleY = scaleX
				transformOrigin = anchor ?: TransformOrigin.Center
			}
		}
	}

fun Modifier.glass(color: Color, shape: Shape, strength: () -> Float = { 1f }): Modifier =
	clip(shape).composed {
		val sigma = with(LocalDensity.current) { Look.backdropBlur.toPx() }
		val underlay = Look.underlay
		val full = remember(sigma, underlay) { backdropFilter(sigma, underlay) }
		drawBehind {
			val amount = strength().coerceIn(0f, 1f)
			if (amount <= 0f) return@drawBehind
			val backdrop =
				if (amount >= 1f) full
				else backdropFilter(sigma * amount, 1f - (1f - underlay) * amount)
			if (backdrop != null) drawIntoCanvas { canvas ->
				canvas.skiaCanvas.saveLayer(Canvas.SaveLayerRec(Rect.makeWH(size.width, size.height), REPLACE, backdrop))
				canvas.skiaCanvas.restore()
			}
			drawRect(color.copy(alpha = color.alpha * amount))
		}
	}

private fun backdropFilter(sigma: Float, underlay: Float): ImageFilter? {
	val blurred =
		if (sigma > 0f) ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
		else null
	return if (underlay >= 1f) blurred
	else ImageFilter.makeColorFilter(faded(underlay), blurred, null)
}

fun Modifier.window(
	color: Color,
	border: Color,
	shape: Shape,
	floating: Boolean = false
): Modifier =
	(if (floating) dropShadow(shape) else this)
		.border(Look.outlineWidth, if (Look.outlines) border else Color.Transparent, shape)
		.glass(color, shape)
		.clip(if (Look.outlines) Inset(shape, Look.outlineWidth / 2) else RectangleShape)
		.consumeClicks()

private data class Inset(private val shape: Shape, private val inset: Dp) : Shape {
	override fun createOutline(
		size: Size,
		layoutDirection: LayoutDirection,
		density: Density
	): Outline {
		val edge = with(density) { inset.toPx() }
		val inner =
			Size(
				(size.width - edge * 2).coerceAtLeast(0f),
				(size.height - edge * 2).coerceAtLeast(0f)
			)
		val path = Path().apply { addOutline(shape.createOutline(inner, layoutDirection, density)) }
		path.translate(Offset(edge, edge))
		return Outline.Generic(path)
	}
}

fun Modifier.dropShadow(shape: Shape, close: Boolean = false): Modifier =
	drawWithCache {
		val body = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
		val drop = (if (close) CLOSE_SHADOW_DROP else SHADOW_DROP).toPx()
		val blur = (if (close) CLOSE_SHADOW_BLUR else SHADOW_BLUR).toPx()
		val reach = blur * 3
		val shadow =
			Paint().apply {
				color = (if (close) CLOSE_SHADOW_COLOR else SHADOW_COLOR).toArgb()
				maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur)
			}
		onDrawBehind {
			if (!Look.shadows) return@onDrawBehind
			clipPath(body, ClipOp.Difference) {
				clipRect(
					-reach,
					drop - reach,
					size.width + reach,
					size.height + drop + reach
				) {
					drawIntoCanvas { canvas ->
						val skia = canvas.skiaCanvas
						skia.saveLayer(Canvas.SaveLayerRec(null, null, DRAWN))
						skia.saveLayer(null, WITHIN)
						skia.translate(0f, drop)
						skia.drawPath(body.asSkiaPath(), shadow)
						skia.restore()
						skia.restore()
					}
				}
			}
		}
	}

private fun faded(fraction: Float) =
	ColorFilter.makeMatrix(
		ColorMatrix(
			1f, 0f, 0f, 0f, 0f,
			0f, 1f, 0f, 0f, 0f,
			0f, 0f, 1f, 0f, 0f,
			0f, 0f, 0f, fraction, 0f,
		)
	)
