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
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import kotlin.math.roundToInt

/**
 * A single line of text. When it is too long for the room it is given it is cut off, and
 * resting the pointer on it has it slide along until its end has come into view, wait, and
 * slide back, for as long as the pointer stays. Taking the pointer away slides it home from
 * wherever it had got to.
 *
 * The way back is always the quick one: reading starts over at the beginning, and the sooner
 * the text is there the better.
 */
@Composable
fun SlidingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = Type.body,
) {
    // How many pixels of the text do not fit, found out when it is laid out
    var overflow by remember { mutableIntStateOf(0) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pixelsPerSecond = with(LocalDensity.current) { SlideSpeed.toPx() }

    // How far along the text is, from 0 at home to 1 with its end in view
    val slide = remember { Animatable(0f) }
    LaunchedEffect(hovered, overflow) {
        if (!hovered || overflow == 0) {
            slide.animateTo(0f, tween(BACK_MILLIS))
            return@LaunchedEffect
        }
        val outMillis = (overflow / pixelsPerSecond * 1000).roundToInt().coerceAtLeast(BACK_MILLIS)
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
                    // Moved by whole pixels, which keeps a pixel font sharp on the way
                    placeable.place(-(slide.value * overflow).roundToInt(), 0)
                }
            }
    )
}

/** How fast the text moves towards its end: slow enough to read along */
private val SlideSpeed = 28.dp

/** Time before the text sets off, so that passing over it does not set it moving */
private const val REST_MILLIS = 350

/** Time at rest with the end in view */
private const val END_REST_MILLIS = 900

/** Time to slide home, however far that is */
private const val BACK_MILLIS = 250
