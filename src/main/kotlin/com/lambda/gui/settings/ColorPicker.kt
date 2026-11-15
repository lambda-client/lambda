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

package com.lambda.gui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lambda.config.settings.complex.ColorSetting
import com.lambda.gui.component.MenuState
import com.lambda.gui.component.NumberInput
import com.lambda.gui.component.Popout
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.scrubbable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.gui.theme.toCompose
import kotlin.math.roundToInt
import java.awt.Color as AwtColor

/**
 * A colour setting: its name and a swatch, which opens a picker beside the row when clicked.
 * The picker works in hue, saturation and brightness, and offers the channels and a hex code
 * to type.
 */
@Composable
internal fun ColorPicker(setting: ColorSetting) {
    val color by observe { setting.value }
    // Kept like a menu's, so clicking the row again shuts the picker it opened
    val picker = remember { MenuState() }

    Labeled(setting.name, Modifier.mouseClickable(onClick = picker::toggle)) {
        Box(Modifier.padding(end = 2.dp)) {
            Spacer(
                Modifier
                    .size(20.dp, 9.dp)
                    .clip(RoundedCornerShape(Radius.ExtraSmall))
                    .drawBehind {
                        drawCheckerboard()
                        drawRect(color.toCompose())
                    }
            )
            Popout(picker.isOpen, onDismiss = picker::dismiss) {
                Column(Modifier.width(PickerWidth)) { Picker(color) { setting.value = it } }
            }
        }
    }
}

private val PickerWidth = 130.dp

@Composable
private fun Picker(color: AwtColor, onColorChange: (AwtColor) -> Unit) {
    val colors = LambdaTheme.colors

    // Hue and saturation are kept here, because the colour alone forgets them: every grey has
    // no hue and black has no saturation, and the picker should not jump when dragged there.
    var hsb by remember { mutableStateOf(AwtColor.RGBtoHSB(color.red, color.green, color.blue, null)) }
    var adopted by remember { mutableStateOf(color) }
    if (color != adopted) {
        adopted = color
        if (AwtColor.HSBtoRGB(hsb[0], hsb[1], hsb[2]) and 0xFFFFFF != color.rgb and 0xFFFFFF) {
            hsb = AwtColor.RGBtoHSB(color.red, color.green, color.blue, null)
        }
    }

    fun pick(hue: Float = hsb[0], saturation: Float = hsb[1], brightness: Float = hsb[2], alpha: Int = color.alpha) {
        hsb = floatArrayOf(hue, saturation, brightness)
        val rgb = AwtColor.HSBtoRGB(hue, saturation, brightness)
        onColorChange(AwtColor(rgb and 0xFFFFFF or (alpha shl 24), true))
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // Saturation across, brightness up
        Spacer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(Radius.ExtraSmall))
                .drawBehind {
                    drawRect(Color.hsv(hsb[0] * 360f, 1f, 1f))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val center = Offset(hsb[1] * size.width, (1f - hsb[2]) * size.height)
                    drawCircle(Color.Black, 3.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White, 3.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                }
                .scrubbable { position, size ->
                    pick(
                        saturation = (position.x / size.width).coerceIn(0f, 1f),
                        brightness = 1f - (position.y / size.height).coerceIn(0f, 1f)
                    )
                }
        )
        Bar(hsb[0], onChange = { pick(hue = it) }) {
            drawRect(Brush.horizontalGradient(HUES))
        }
        Bar(color.alpha / 255f, onChange = { pick(alpha = (it * 255).roundToInt()) }) {
            drawCheckerboard()
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.toCompose().copy(alpha = 1f))))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Channel("R", color.red) { onColorChange(AwtColor(it, color.green, color.blue, color.alpha)) }
            Channel("G", color.green) { onColorChange(AwtColor(color.red, it, color.blue, color.alpha)) }
            Channel("B", color.blue) { onColorChange(AwtColor(color.red, color.green, it, color.alpha)) }
            Channel("A", color.alpha) { onColorChange(AwtColor(color.red, color.green, color.blue, it)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hex", color = colors.onSurfaceVariant, style = Type.label)
            TextInput(color.toHex(), { text -> parseHex(text)?.let(onColorChange) }, Modifier.weight(1f), style = Type.label)
        }
    }
}

/** A horizontal strip painted by [background], with a thumb at [fraction] that drags along it */
@Composable
private fun Bar(fraction: Float, onChange: (Float) -> Unit, background: DrawScope.() -> Unit) {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Radius.ExtraSmall))
            .drawBehind {
                background()
                val thumb = Size(4.dp.toPx(), size.height)
                val left = Offset(fraction * (size.width - thumb.width), 0f)
                val corner = CornerRadius(1.dp.toPx())
                drawRoundRect(Color.White, left, thumb, corner)
                drawRoundRect(Color.Black, left, thumb, corner, style = Stroke(0.5.dp.toPx()))
            }
            .scrubbable { position, size -> onChange((position.x / size.width).coerceIn(0f, 1f)) }
    )
}

@Composable
private fun RowScope.Channel(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LambdaTheme.colors.onSurfaceVariant, style = Type.label)
        NumberInput(value, { onChange(it.roundToInt().coerceIn(0, 255)) }, Modifier.fillMaxWidth())
    }
}

/** The grey chequers a translucent colour is shown over */
private fun DrawScope.drawCheckerboard() {
    val cell = 4.dp.toPx()
    drawRect(Color(0xFFCCCCCC))
    var row = 0
    while (row * cell < size.height) {
        var column = row % 2
        while (column * cell < size.width) {
            drawRect(Color(0xFF999999), Offset(column * cell, row * cell), Size(cell, cell))
            column += 2
        }
        row++
    }
}

private fun AwtColor.toHex() =
    if (alpha == 255) "#%02X%02X%02X".format(red, green, blue)
    else "#%02X%02X%02X%02X".format(red, green, blue, alpha)

/** Reads "#RRGGBB" or "#RRGGBBAA", with or without the hash */
private fun parseHex(text: String): AwtColor? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    val channels = digits.chunked(2).map { it.toIntOrNull(16) ?: return null }
    return AwtColor(channels[0], channels[1], channels[2], channels.getOrElse(3) { 255 })
}

private val HUES = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }
