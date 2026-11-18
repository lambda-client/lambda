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

package com.lambda.gui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.gui.component.SlidingText
import com.lambda.gui.core.observe
import com.lambda.module.modules.client.Style
import com.lambda.util.NamedEnum
import com.lambda.util.stream

/**
 * Every colour the interface uses, named for what it is for. Built from the [Style] module's
 * settings, so a colour picked there shows up everywhere at once.
 */
@Immutable
data class Palette(
    /** Accent: sliders, checked boxes, the selected tab, the outline of the module being edited */
    val primary: Color,
    val secondary: Color,
    /** Fill of windows, tinted over the frosted game */
    val surface: Color,
    /** Fill of what opens over a window: a module's settings, menus, dialogs */
    val panel: Color,
    /** Shade over what is pressed in: the module whose settings are open */
    val inset: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val header: Color,
    val onHeader: Color,
    val moduleEnabled: Color,
    val onModuleEnabled: Color,
    /** Fill of dropdowns, buttons and unchecked boxes */
    val control: Color,
    /** Fill of the list a dropdown opens */
    val controlMenu: Color,
    val controlSelected: Color,
    val onControl: Color,
    /** The unfilled part of a slider */
    val track: Color,
    /** Fill of text fields */
    val input: Color,
    val border: Color,
    val textShadow: Color,
) {
    /** Text drawn on [primary]: whichever of dark and white stands out */
    val onPrimary: Color = if (primary.luminance() > 0.35f) Color(0xFF1B1024) else Color.White

    /** Laid over a surface to lift something off it: the heading of a group, the chosen tab */
    val raised: Color = onSurface.copy(alpha = 0.11f)

    /** Laid under the row the pointer is on, which shows how far it reaches */
    val hover: Color = onSurface.copy(alpha = 0.06f)
}

/**
 * How thick outlines are: one pixel at 1080p, and never less than one anywhere. Outlines are
 * there to finish an edge, not to be looked at.
 */
val Hairline = 0.5.dp

/** Corner radii, by how much room the component has */
object Radius {
    /** Checkboxes, colour swatches, the small parts inside a control */
    val ExtraSmall = 1.dp

    /** Controls and the blocks settings are grouped in */
    val Small = 2.dp

    /** Windows, menus and tooltips */
    val Medium = 3.dp

    /** The panels that pop out of a window, and dialogs */
    val Large = 5.dp
}

/**
 * The fonts the interface can be set in. Each comes with the size it reads well at: the
 * Minecraft font is drawn on a grid of twelve to the em, so at 12 sp one of its pixels is one
 * dp, which is two screen pixels at 1080p, the size of Minecraft's own text at GUI scale 2.
 */
enum class GuiFont(override val displayName: String, val size: TextUnit, val smallSize: TextUnit, val titleWeight: FontWeight) : NamedEnum {
    Minecraft("Minecraft", 12.sp, 12.sp, FontWeight.Normal),
    FiraSans("Fira Sans", 10.sp, 9.sp, FontWeight.Bold),
    System("System", 10.sp, 9.sp, FontWeight.Bold);

    val family: FontFamily by lazy {
        fun font(file: String, weight: FontWeight) =
            Font(file, "fonts/$file".stream.use { it.readAllBytes() }, weight)
        when (this) {
            Minecraft -> FontFamily(font("MinecraftDefault-Regular.ttf", FontWeight.Normal))
            FiraSans -> FontFamily(font("FiraSans-Regular.ttf", FontWeight.Normal), font("FiraSans-Bold.ttf", FontWeight.Bold))
            System -> FontFamily.SansSerif
        }
    }
}

/** The text styles of a [GuiFont]. Lines are exactly as tall as the type, so rows are spaced by their padding alone. */
@Immutable
class Typography(font: GuiFont, scale: Float = 1f) {
    val title = compact(font, font.size * scale, font.titleWeight)
    val body = compact(font, font.size * scale)

    /** For what is secondary to the text beside it. No smaller than [body] in a pixel font, which cannot shrink. */
    val label = compact(font, font.smallSize * scale)

    private fun compact(font: GuiFont, size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = font.family,
        fontWeight = weight,
        fontSize = size,
        // Only between the lines of text that wraps: the first and the last keep to the type.
        // A pixel font fills its grid, an outline font needs air or its lines touch.
        lineHeight = if (font == GuiFont.Minecraft) size else size * 1.25f,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
}

/** The text styles of the font in use: `Type.body`, `Type.title`, `Type.label` */
object Type {
    val title: TextStyle @Composable @ReadOnlyComposable get() = LocalTypography.current.title
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalTypography.current.body
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalTypography.current.label
}

object LambdaTheme {
    val colors: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

private val LocalPalette = staticCompositionLocalOf<Palette> { error("No LambdaTheme in this composition") }
private val LocalTypography = staticCompositionLocalOf<Typography> { error("No LambdaTheme in this composition") }

/** Colour text takes when none is given. Set it for a subtree with [ContentColor]. */
val LocalContentColor = compositionLocalOf { Color.White }

/**
 * How much glass blurs the interface beneath it, as the sigma of a Gaussian; zero for not at
 * all. See [com.lambda.gui.component.glass].
 */
val LocalBackdropBlur = staticCompositionLocalOf { 0.dp }

/**
 * How much of the interface beneath a pane of glass is left to show through it, from all of it
 * at one to none at zero. See [com.lambda.gui.component.glass].
 */
val LocalUnderlay = staticCompositionLocalOf { 1f }

/** How tall rows and controls are at least, see [com.lambda.gui.component.RowHeight] */
val LocalRowHeight = staticCompositionLocalOf { 12.dp }

/** Whether windows, panels and menus are drawn with a hairline border, see [com.lambda.gui.component.window] */
val LocalOutlines = staticCompositionLocalOf { true }

/** How tall a line of the Minecraft font is, in dp, and so how tall any font's may be. A shade under, as heights round up. */
private const val LINE_HEIGHT = 10.95f

@Composable
fun LambdaTheme(content: @Composable () -> Unit) {
    val palette by observe { Style.palette }
    // Half the radius the game behind is blurred with, which is about what a sigma amounts to
    val backdropBlur by observe { if (Style.blur) (Style.blurRadius / 2f).dp else 0.dp }
    val font by observe { Style.font }
    val outlines by observe { Style.outlines }
    // Every row is built around the line of the Minecraft font. A font that asks for a taller
    // line at its size, for more room above and below its letters, is set that much smaller:
    // its lines keep the room they ask for, and the rows keep their height.
    val measurer = rememberTextMeasurer()
    // On top of that comes the size the player asks for, which rows that hold text grow to fit.
    val fontSize by observe { Style.fontSize }
    val typography = remember(font, fontSize) {
        if (font == GuiFont.Minecraft) return@remember Typography(font, fontSize)
        val line = measurer.measure("Ag", Typography(font).body, density = Density(1f)).size.height
        Typography(font, (LINE_HEIGHT / line).coerceAtMost(1f) * fontSize)
    }
    val underlay by observe { 1f - Style.layerFade }
    val rowHeight by observe { Style.elementHeight.dp }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTypography provides typography,
        // Selected text is marked in the accent colour rather than in Compose's own blue
        LocalTextSelectionColors provides TextSelectionColors(palette.primary, palette.primary.copy(alpha = 0.4f)),
        LocalContentColor provides palette.onSurface,
        LocalBackdropBlur provides backdropBlur,
        LocalOutlines provides outlines,
        LocalUnderlay provides underlay,
        LocalRowHeight provides rowHeight,
        content = content
    )
}

@Composable
fun ContentColor(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)

/** The shadow a [Text] is drawn over when its style does not bring one. Set for a subtree with [SoftTextShadow]. */
private val LocalTextShadow = compositionLocalOf<Shadow?> { null }

/**
 * Draws all the text in [content] over a soft shadow, just enough to lift it off glass that
 * lets a bright or busy background through.
 */
@Composable
fun SoftTextShadow(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val color = LambdaTheme.colors.textShadow
    val shadow = remember(density, color) {
        with(density) { Shadow(color, Offset(0f, 0.5.dp.toPx()), blurRadius = 1.5.dp.toPx()) }
    }
    CompositionLocalProvider(LocalTextShadow provides shadow, content = content)
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = Type.body,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    // A single line too long for its room is never cut short with dots: it slides under the pointer
    if (maxLines == 1) return SlidingText(text, modifier, color, style)
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, shadow = style.shadow ?: LocalTextShadow.current),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * This style with a hard drop shadow, for text that sits on a busy or translucent background.
 * The shadow is the theme's unless a [color] is given.
 */
@Composable
fun TextStyle.shadowed(color: Color = LambdaTheme.colors.textShadow): TextStyle {
    val offset = with(LocalDensity.current) { 1.dp.toPx() }
    return copy(shadow = Shadow(color, Offset(offset, offset)))
}

fun java.awt.Color.toCompose() = Color(red, green, blue, alpha)
