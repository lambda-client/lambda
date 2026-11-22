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

import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lambda.gui.GuiConfig
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val WHEEL_STEP = 14.dp
private val GLIDE = tween<Float>(durationMillis = 520, easing = EaseOutQuint)
private const val SCROLLBAR_FADE_MILLIS = 150

@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.wheelScrollable(scroll)) {
        Column(Modifier.verticalScroll(scroll), content = content)
        if (scroll.maxValue > 0) Scrollbar(rememberScrollbarAdapter(scroll), Modifier.matchParentSize())
    }
}

@Composable
fun Scrollbar(adapter: ScrollbarAdapter, modifier: Modifier) {
    val colors = LambdaTheme.colors
    Box(modifier) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 12.dp,
                thickness = 2.dp,
                shape = RoundedCornerShape(1.dp),
                hoverDurationMillis = SCROLLBAR_FADE_MILLIS,
                unhoverColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                hoverColor = colors.onSurface
            )
        )
    }
}

fun Modifier.wheelScrollable(state: ScrollableState): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val notch = with(LocalDensity.current) { WHEEL_STEP.toPx() }
        val glide = remember { WheelGlide() }
        pointerInput(state, notch) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.first()
                    val distance = change.scrollDelta.y * notch * GuiConfig.scrollSpeed
                    val canFollow =
                        if (distance > 0) state.canScrollForward
                        else state.canScrollBackward
                    if (distance == 0f || !canFollow || change.isConsumed) continue
                    change.consume()

                    val total =
                        distance +
                                if (glide.remaining * distance > 0) glide.remaining
                                else 0f
                    glide.job?.cancel()
                    glide.job =
                        scope.launch(Motion.SCROLL) {
                            var travelled = 0f
                            glide.remaining = total
                            try {
                                state.scroll(MutatePriority.UserInput) {
                                    animate(
                                        0f,
                                        total,
                                        animationSpec = GLIDE
                                    ) { value, _ ->
                                        scrollBy(value - travelled)
                                        travelled = value
                                        glide.remaining = total - value
                                    }
                                }
                            } finally {
                                if (glide.job === coroutineContext[Job]) {
                                    glide.remaining = 0f
                                }
                            }
                        }
                }
            }
        }
    }

private class WheelGlide {
    var remaining = 0f
    var job: Job? = null
}
