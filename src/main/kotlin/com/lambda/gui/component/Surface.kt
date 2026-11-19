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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.Hairline
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.LocalBackdropBlur
import com.lambda.gui.theme.LocalOutlines
import com.lambda.gui.theme.LocalUnderlay
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

/**
 * Makes the element a pane of frosted glass in [shape]: everything behind it is blurred, and
 * [color] tints the result.
 *
 * "Behind" has two halves. The game is frosted by the compositor wherever the interface covers
 * it, see [com.lambda.gui.render.GuiCompositor]. What the interface itself drew underneath, such
 * as the window a settings panel has opened over, is blurred here, so it reads as part of the
 * same background rather than showing through sharp or being cut out.
 *
 * That half is also faded, to [LocalUnderlay] of what it was: the modules of the window under a
 * settings panel are still there, faintly, and the game takes their place behind the glass.
 *
 * With the blur switched off the pane simply replaces what is beneath it: stacked panes of
 * translucent text over text would be unreadable.
 */
fun Modifier.glass(color: Color, shape: Shape): Modifier = clip(shape).composed {
    val sigma = with(LocalDensity.current) { LocalBackdropBlur.current.toPx() }
    val underlay = LocalUnderlay.current
    val blur = remember(sigma, underlay) {
        if (sigma <= 0f) return@remember null
        val blurred = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
        // Thinned out as well as blurred: where the interface is fainter the compositor lets
        // more of the game through, so what the pane lies over does not crowd what is on it
        if (underlay >= 1f) blurred else ImageFilter.makeColorFilter(faded(underlay), blurred, null)
    }
    drawBehind {
        if (blur == null) {
            drawRect(color, blendMode = BlendMode.Src)
            return@drawBehind
        }
        drawIntoCanvas { canvas ->
            // A layer that starts out as the blurred copy of what is under it and is put back
            // in place of the original, not over it, or the sharp version would show through
            canvas.skiaCanvas.saveLayer(Canvas.SaveLayerRec(Rect.makeWH(size.width, size.height), Replace, blur))
            canvas.skiaCanvas.restore()
        }
        drawRect(color)
    }
}

/**
 * The look every window, panel and menu shares: frosted glass in [shape] with a hairline
 * border, unless outlines are switched off in the style. One that is [floating] over the rest
 * of the interface also casts a shadow.
 */
fun Modifier.window(color: Color, border: Color, shape: Shape, floating: Boolean = false): Modifier =
    (if (floating) dropShadow(shape) else this)
        .glass(color, shape)
        .composed { if (LocalOutlines.current) Modifier.border(Hairline, border, shape) else Modifier }
        .consumeClicks()

/**
 * Casts a soft shadow from the element onto the interface beneath it: it is what separates a
 * panel from the window it has opened over when both are glass of nearly the same tint.
 *
 * The shadow only falls on what the interface has already drawn, never on the bare game. To
 * the compositor every pixel of the interface is something to frost the game behind and to
 * glow around, see [com.lambda.gui.render.GuiCompositor], and a shadow lying on the world would
 * be given both. Nor is anything drawn inside [shape]: the element is glass, and a shadow
 * underneath it would darken what shows through.
 *
 * A [close] shadow is darker and tighter: for the small popups that open over a settings panel,
 * whose soft shadow would be lost on the panel's dark glass.
 */
fun Modifier.dropShadow(shape: Shape, close: Boolean = false): Modifier = drawWithCache {
    val body = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val drop = (if (close) CloseShadowDrop else ShadowDrop).toPx()
    val blur = (if (close) CloseShadowBlur else ShadowBlur).toPx()
    val reach = blur * 3
    val shadow = Paint().apply {
        color = (if (close) CloseShadowColor else ShadowColor).toArgb()
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur)
    }
    onDrawBehind {
        if (!Look.shadows) return@onDrawBehind
        clipPath(body, ClipOp.Difference) {
            clipRect(-reach, drop - reach, size.width + reach, size.height + drop + reach) {
                drawIntoCanvas { canvas ->
                    val skia = canvas.skiaCanvas
                    // A layer that starts out as a stencil of what the interface has drawn here.
                    // The shadow is drawn on a layer of its own and put onto the stencil keeping
                    // only what lies inside it: a shadow cut to the shape of the interface.
                    skia.saveLayer(Canvas.SaveLayerRec(null, null, Drawn))
                    skia.saveLayer(null, Within)
                    skia.translate(0f, drop)
                    skia.drawPath(body.asSkiaPath(), shadow)
                    skia.restore()
                    skia.restore()
                }
            }
        }
    }
}

private val ShadowColor = Color.Black.copy(alpha = 0.55f)
private val ShadowBlur = 5.dp
private val ShadowDrop = 2.dp
private val CloseShadowColor = Color.Black.copy(alpha = 0.85f)
private val CloseShadowBlur = 3.dp
private val CloseShadowDrop = 2.dp

/**
 * Turns what has been drawn into its stencil: black wherever there is anything at all. The
 * gain is the compositor's, so the stencil covers exactly what it treats as interface.
 */
private val Drawn = ImageFilter.makeColorFilter(
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

/** Scales the opacity of what it is applied to by [fraction] */
private fun faded(fraction: Float) = ColorFilter.makeMatrix(
    ColorMatrix(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, fraction, 0f,
    )
)

private val Replace = Paint().apply { blendMode = org.jetbrains.skia.BlendMode.SRC }
private val Within = Paint().apply { blendMode = org.jetbrains.skia.BlendMode.SRC_IN }
