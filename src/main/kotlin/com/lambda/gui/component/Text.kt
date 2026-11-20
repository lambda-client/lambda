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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.LocalContentColor
import com.lambda.gui.theme.Type

private val LocalTextShadow = compositionLocalOf<Shadow?> { null }

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = Type.body,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
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

@Composable
fun ContentColor(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)

@Composable
fun SoftTextShadow(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val color = LambdaTheme.colors.textShadow
    val shadow =
        remember(density, color) {
            with(density) { Shadow(color, Offset(0f, 0.5.dp.toPx()), blurRadius = 1.5.dp.toPx()) }
        }
    CompositionLocalProvider(LocalTextShadow provides shadow, content = content)
}

@Composable
fun TextStyle.shadowed(color: Color = LambdaTheme.colors.textShadow): TextStyle {
    val offset = with(LocalDensity.current) { 1.dp.toPx() }
    return copy(shadow = Shadow(color, Offset(offset, offset)))
}
