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

package com.lambda.gui.clickgui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.gui.component.Expandable
import com.lambda.gui.component.LocalPopupHost
import com.lambda.gui.component.PopupHost
import com.lambda.gui.component.dropShadow
import com.lambda.gui.component.mouseDraggable
import com.lambda.gui.component.window
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.ResizeVerticalIcon
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.menu.MENU_BAR_HEIGHT
import com.lambda.gui.settings.ModuleSettings
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.module.ModuleTag
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MIN_WIDTH = 130.dp
private val MIN_HEIGHT = 120.dp
private val MIN_GRIP_HEIGHT = 3.dp
private val INSET_SHADOW = 5.dp
private const val SETTLE_MILLIS = 200L
private val WIDTH_CHANGE = spring<Float>(stiffness = Spring.StiffnessMedium)

@Composable
internal fun SettingsPanel(
    panel: SettingsPanelState,
    open: Boolean,
    windows: Map<ModuleTag, Rect>,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = panel.row
    val visibility = remember(LocalShown.current) { MutableTransitionState(false) }
    val wanted = open && panel.settled && !row.isEmpty
    val popups = remember { PopupHost() }
    SideEffect { popups.closing = !wanted }
    visibility.targetState = wanted
    if (!open && visibility.isIdle && !visibility.currentState) {
        SideEffect(onClosed)
        return
    }
    if (row.isEmpty) return

    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize
    var size by remember { mutableStateOf(IntSize.Zero) }
    var unfolded by remember { mutableStateOf(0) }

    val scroll = rememberScrollState()
    val screenHeight = with(density) { screen.height.toDp() }
    val barBottom = with(density) { MENU_BAR_HEIGHT.toPx() }
    val window = windows[panel.module.tag]
    val top =
        (
            if (window == null) row.top
            else {
                row.top.coerceIn(
                    window.top,
                    (window.bottom - row.height).coerceAtLeast(window.top)
                )
            }
        ).coerceAtLeast(barBottom)
    val below = with(density) { (screen.height - top).toDp() }.coerceAtLeast(MIN_HEIGHT)
    val highest = screenHeight - MENU_BAR_HEIGHT
    val maxHeight = (below + panel.extension).coerceAtMost(highest)

    val overlap = with(density) { 1.dp.toPx() }
    val fitsRight = row.right - overlap + size.width <= screen.width
    val fitsLeft = row.left + overlap - size.width >= 0
    val flipped = !fitsRight && (fitsLeft || row.left > screen.width - row.right)
    val lift = (top + unfolded - screen.height).coerceIn(0f, top - barBottom)

    val shape =
        RoundedCornerShape(
            topStart = if (flipped || lift > 0f) Look.panelRounding else 0.dp,
            topEnd = if (!flipped || lift > 0f) Look.panelRounding else 0.dp,
            bottomEnd = Look.panelRounding,
            bottomStart = Look.panelRounding,
        )
    val seam =
        if (flipped) Alignment.TopEnd
        else Alignment.TopStart

    Box(
        modifier
            .offset {
                val room = (screen.width - size.width).coerceAtLeast(0)
                IntOffset(
                    if (flipped) (row.left + overlap).roundToInt().coerceIn(screen.width - room, screen.width)
                    else (row.right - overlap).roundToInt().coerceIn(0, room),
                    (top - lift).roundToInt()
                )
            }
            .size(0.dp)
            .wrapContentSize(seam, unbounded = true)
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            modifier = Modifier
                .onSizeChanged { unfolded = it.height }
                .onGloballyPositioned { popups.place(it.boundsInWindow()) }
                .dropShadow(RoundedCornerShape(Look.panelRounding)),
            enter = expandIn(Motion.panels, seam),
            exit = shrinkOut(Motion.panels, seam),
        ) {
            val colors = LambdaTheme.colors
            Box(
                Modifier
                    .widthIn(MIN_WIDTH, Look.settingsWidth)
                    .heightIn(max = maxHeight)
                    .widthOfContent()
                    .onSizeChanged { size = it }
                    .window(colors.panel, colors.border, shape)
            ) {
                CompositionLocalProvider(LocalPopupHost provides popups) {
                    Column(Modifier.padding(Look.panelPadding)) {
                        ModuleSettings(
                            panel.module,
                            panel.page,
                            { panel.page = it },
                            scroll
                        )
                    }
                }
                if (scroll.maxValue > 0 || lift > 0f) {
                    Grip(
                        onDragStart = {
                            panel.extension =
                                panel.extension.coerceAtMost(
                                    with(density) { lift.toDp() }
                                )
                        },
                        onDrag = { up ->
                            val needed =
                                panel.extension + with(density) { scroll.maxValue.toDp() }
                            panel.extension =
                                (panel.extension + up).coerceIn(
                                    0.dp,
                                    needed
                                        .coerceAtMost(highest - below)
                                        .coerceAtLeast(0.dp)
                                )
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
internal fun InlineSettings(panel: SettingsPanelState, open: Boolean) {
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            awaitFrames(SETTLE_MILLIS)
            intoView.bringIntoView()
        }
    }
    Expandable(open) {
        val colors = LambdaTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(intoView)
                .background(colors.panel)
                .background(colors.inset.copy(alpha = colors.inset.alpha / 4))
                .drawWithCache {
                    val depth = INSET_SHADOW.toPx().coerceAtMost(size.height / 2)
                    val dark = colors.inset.copy(alpha = colors.inset.alpha / 2)
                    val top = Brush.verticalGradient(listOf(dark, Color.Transparent), 0f, depth)
                    val bottom =
                        Brush.verticalGradient(
                            listOf(Color.Transparent, dark),
                            size.height - depth,
                            size.height
                        )
                    onDrawBehind {
                        drawRect(top, size = Size(size.width, depth))
                        drawRect(
                            bottom,
                            Offset(0f, size.height - depth),
                            Size(size.width, depth)
                        )
                    }
                }
                .padding(Look.panelPadding / 2)
        ) { ModuleSettings(panel.module, panel.page, { panel.page = it }, scroll = null) }
    }
}

private fun Modifier.widthOfContent(): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        var width by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
        layout { measurable, constraints ->
            val wanted =
                measurable.maxIntrinsicWidth(constraints.maxHeight)
                    .coerceIn(constraints.minWidth, constraints.maxWidth).toFloat()
            val current = width ?: Animatable(wanted).also { width = it }
            if (current.targetValue != wanted) {
                scope.launch { current.animateTo(wanted, WIDTH_CHANGE) }
            }

            val placeable =
                measurable.measure(
                    Constraints.fixedWidth(current.value.roundToInt()).copy(
                        maxHeight = constraints.maxHeight
                    )
                )
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

@Composable
private fun Grip(
    onDragStart: () -> Unit,
    onDrag: (up: Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val color = LambdaTheme.colors.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(Look.panelPadding.coerceAtLeast(MIN_GRIP_HEIGHT))
            .pointerHoverIcon(ResizeVerticalIcon)
            .mouseDraggable(onDragStart = onDragStart) { delta ->
                onDrag(with(density) { -delta.y.toDp() })
            }
    ) {
        Spacer(Modifier.size(16.dp, 1.dp).background(color, RoundedCornerShape(50)))
    }
}
