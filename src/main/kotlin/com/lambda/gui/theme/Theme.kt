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

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.gui.core.Observation
import com.lambda.gui.core.Observations
import com.lambda.gui.core.observe
import com.lambda.module.modules.client.Style
import com.lambda.util.NamedEnum
import com.lambda.util.stream

private const val LINE_HEIGHT = 10.95f

private val ON_LIGHT_ACCENT = Color(0xFF1B1024)

@Immutable
data class Palette(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val panel: Color,
    val inset: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val header: Color,
    val onHeader: Color,
    val moduleEnabled: Color,
    val onModuleEnabled: Color,
    val control: Color,
    val controlSelected: Color,
    val onControl: Color,
    val track: Color,
    val input: Color,
    val border: Color,
    val textShadow: Color,
) {
    val onPrimary = primary.contrast()
    val onSecondary = secondary.contrast()
    val trackFilled = if (onControl.luminance() > 0.5f) primary else primary.copy(alpha = 0.6f)
    val raised = onSurface.copy(alpha = 0.11f)
    val hover = onSurface.copy(alpha = 0.06f)

    private fun Color.contrast() = if (luminance() > 0.2f) ON_LIGHT_ACCENT else Color.White
}

internal object Look {
    private val observations = Observations()

    val windowRounding by style { Style.windowRounding.dp }
    val panelRounding by style { Style.panelRounding.dp }
    val menuRounding by style { Style.menuRounding.dp }
    val controlRounding by style { Style.controlRounding.dp }
    val boxRounding get() = controlRounding / 2

    val outlines by style { Style.outlines }
    val outlineWidth by style { Style.outlineWidth.dp }
    val shadows by style { Style.shadows }
    val emboss by style { Style.emboss }
    val backdropBlur by style { if (Style.blur) (Style.blurRadius / 2f).dp else 0.dp }
    val underlay by style { 1f - Style.layerFade }

    val rowHeight by style { Style.elementHeight.dp }
    val windowWidth by style { Style.windowWidth.dp }
    val settingsWidth by style { Style.settingsWidth.dp }
    val windowGap by style { Style.windowGap.dp }
    val windowTop by style { Style.windowTop.dp }
    val modulePadding by style { Style.modulePadding.dp }
    val panelPadding by style { Style.panelPadding.dp }
    val settingSpacing by style { Style.settingSpacing.dp }
    val groupIndent by style { Style.groupIndent.dp }

    fun refresh() = observations.refresh()

    private fun <T> style(read: () -> T): State<T> = Observation(read).also { observations.active += it }.state
}

internal object Motion : MotionDurationScale {
    private const val PAGE_MILLIS = 160
    private val UNFOLDING = spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntSize.VisibilityThreshold)

    val SCROLL = object : MotionDurationScale {
        override val scaleFactor get() = if (Style.smoothScrolling) 1f else 0f
    }

    val UNSCALED = object : MotionDurationScale {
        override val scaleFactor get() = 1f
    }

    override val scaleFactor get() = if (Style.animations) 1f / Style.animationSpeed else 0f

    val windows: FiniteAnimationSpec<IntSize> get() = if (Style.windowAnimations) spring() else snap()
    val panels: FiniteAnimationSpec<IntSize> get() = if (Style.panelAnimations) UNFOLDING else snap()
    val pageMillis get() = if (Style.pageAnimations) PAGE_MILLIS else 0
}

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

@Immutable
private class Typography(font: GuiFont, scale: Float = 1f) {
    val title = compact(font, font.size * scale, font.titleWeight)
    val body = compact(font, font.size * scale)
    val label = compact(font, font.smallSize * scale)

    private fun compact(font: GuiFont, size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = font.family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = if (font == GuiFont.Minecraft) size else size * 1.25f,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
}

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

val LocalContentColor = compositionLocalOf { Color.White }

@Composable
fun LambdaTheme(content: @Composable () -> Unit) {
    val palette by observe { Style.palette }
    val font by observe { Style.font }
    val fontSize by observe { Style.fontSize }
    val measurer = rememberTextMeasurer()
    val typography = remember(font, fontSize) {
        if (font == GuiFont.Minecraft) return@remember Typography(font, fontSize)
        val line = measurer.measure("Ag", Typography(font).body, density = Density(1f)).size.height
        Typography(font, (LINE_HEIGHT / line).coerceAtMost(1f) * fontSize)
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTypography provides typography,
        LocalTextSelectionColors provides TextSelectionColors(palette.primary, palette.primary.copy(alpha = 0.4f)),
        LocalContentColor provides palette.onSurface,
        content = content
    )
}
