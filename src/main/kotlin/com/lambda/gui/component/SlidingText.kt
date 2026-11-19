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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.LocalContentColor
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Type
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

private val SLIDE_SPEED = 28.dp
private const val REST_MILLIS = 350
private const val END_REST_MILLIS = 900
private const val BACK_MILLIS = 250

@Composable
fun SlidingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = Type.body,
) {
    var overflow by remember { mutableIntStateOf(0) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pixelsPerSecond = with(LocalDensity.current) { SLIDE_SPEED.toPx() }

    val slide = remember { Animatable(0f) }
    LaunchedEffect(hovered, overflow) {
        if (!hovered || overflow == 0) {
            slide.animateTo(0f, tween(BACK_MILLIS))
            return@LaunchedEffect
        }
        val outMillis = (overflow / pixelsPerSecond * 1000).roundToInt().coerceAtLeast(BACK_MILLIS)
        withContext(Motion.UNSCALED) {
            slide.animateTo(
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = REST_MILLIS + outMillis + END_REST_MILLIS + BACK_MILLIS
                        0f at REST_MILLIS using LinearEasing
                        1f at REST_MILLIS + outMillis
                        1f at REST_MILLIS + outMillis + END_REST_MILLIS
                    }
                )
            )
        }
    }

    Text(
        text = text,
        color = color,
        style = style,
        softWrap = false,
        modifier = modifier
            .hoverable(interactions)
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
                overflow = if (constraints.hasBoundedWidth) (placeable.width - constraints.maxWidth).coerceAtLeast(0) else 0
                layout(placeable.width - overflow, placeable.height) {
                    placeable.place(-(slide.value * overflow).roundToInt(), 0)
                }
            }
    )
}
