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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.lambda.gui.GuiConfig
import com.lambda.gui.Reveal
import com.lambda.gui.Snap
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.SlidingText
import com.lambda.gui.component.Text
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.mouseDraggable
import com.lambda.gui.component.shadowed
import com.lambda.gui.component.window
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.observe
import com.lambda.gui.menu.MENU_BAR_HEIGHT
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Type
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import kotlin.math.absoluteValue

private val REACH = 40.dp
private const val IN_ROW = 1.5f
private val EMBOSS = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent))

@Composable
internal fun CategoryWindow(
    tag: ModuleTag,
    defaultPosition: DpOffset,
    bounds: MutableMap<ModuleTag, Rect>,
    snapping: Boolean,
    onDrop: (Snap.Result?) -> Unit,
    settings: OpenSettings,
    inline: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modules by observe { ModuleRegistry.modules.filter { it.tag == tag && it.showInClickGui.value } }
    if (modules.isEmpty()) return

    val colors = LambdaTheme.colors
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize.toSize()
    val screenHeight = with(density) { screen.height.toDp() }
    val area = Rect(0f, with(density) { MENU_BAR_HEIGHT.toPx() }, screen.width, screen.height)

    val placement by observe { GuiConfig.windows[tag.name] }
    val position = placement?.takeUnless { it.inRow }?.let { DpOffset(it.x.dp, it.y.dp) }
    val collapsed = placement?.collapsed ?: false

    var dragged by remember { mutableStateOf<Offset?>(null) }
    val drop = dragged?.let { corner ->
        val window = Rect(corner, bounds[tag]?.size ?: Size.Zero)
        if (snapping) Snap.snap(window, bounds.filterKeys { it != tag }.values, area, density, with(density) { Look.windowGap.toPx() })
        else Snap.free(window, area)
    }
    if (drop != null) SideEffect { onDrop(drop) }
    val shownPosition = drop?.let {
        with(density) { DpOffset(it.position.x.toDp(), it.position.y.toDp()) }
    } ?: (position ?: defaultPosition).let { at ->
        val screenWidth = with(density) { screen.width.toDp() }
        DpOffset(
            at.x.coerceIn(0.dp, (screenWidth - Look.windowWidth).coerceAtLeast(0.dp)),
            at.y.coerceIn(MENU_BAR_HEIGHT, (screenHeight - REACH).coerceAtLeast(MENU_BAR_HEIGHT)),
        )
    }
    val currentPosition by rememberUpdatedState(shownPosition)
    DisposableEffect(tag) {
        onDispose { bounds -= tag }
    }

    fun place(at: DpOffset, collapsed: Boolean) {
        val inRow = (at.x - defaultPosition.x).value.absoluteValue < IN_ROW && (at.y - defaultPosition.y).value.absoluteValue < IN_ROW
        GuiConfig.windows += tag.name to
            if (inRow) GuiConfig.WindowPlacement.inRow(collapsed)
            else GuiConfig.WindowPlacement(at.x.value, at.y.value, collapsed)
    }
    LaunchedEffect(placement) {
        position?.let { place(it, collapsed) }
    }

    Column(
        modifier = modifier
            .offset(shownPosition.x, shownPosition.y)
            .width(Look.windowWidth)
            .onGloballyPositioned { bounds[tag] = Rect(it.positionInRoot(), it.size.toSize()) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onFocus()
                    }
                }
            }
            .window(colors.surface, colors.border, RoundedCornerShape(Look.windowRounding))
    ) {
        Text(
            text = tag.name,
            color = colors.onHeader,
            style = Type.title.shadowed(),
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.header)
                .mouseDraggable(
                    onDragStart = { dragged = bounds[tag]?.topLeft },
                    onDragEnd = {
                        if (dragged != null) place(currentPosition, collapsed)
                        dragged = null
                        onDrop(null)
                    },
                ) { delta -> dragged = dragged?.plus(delta) }
                .mouseClickable(onRightClick = {
                    if (!collapsed) settings.close(modules)
                    place(currentPosition, !collapsed)
                })
                .padding(horizontal = Look.modulePadding, vertical = 2.dp)
        )

        val shown = LocalShown.current
        val unfolded = remember(shown) { MutableTransitionState(false) }
        unfolded.targetState = shown && !collapsed
        val settled = unfolded.isIdle && unfolded.currentState
        val hanging = modules.mapNotNull(settings::panelOf)
        SideEffect { hanging.forEach { it.settled = settled } }
        AnimatedVisibility(
            visibleState = unfolded,
            enter = expandVertically(Motion.windows),
            exit = shrinkVertically(Motion.windows),
        ) {
            ScrollColumn(Modifier.heightIn(max = (screenHeight - shownPosition.y - 24.dp).coerceAtLeast(48.dp))) {
                modules.forEach { module ->
                    ModuleRow(
                        module = module,
                        isEdited = settings.isOpen(module),
                        pressedIn = !inline,
                        panel = settings.panelOf(module).takeUnless { inline },
                        onEdit = { settings.toggle(module) },
                    )
                    if (inline) InlineSettings(settings.stateOf(module), open = settings.isOpen(module))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleRow(module: Module, isEdited: Boolean, pressedIn: Boolean, panel: SettingsPanelState?, onEdit: () -> Unit) {
    val colors = LambdaTheme.colors
    val enabled by observe { module.isEnabled }
    val fill by animateColorAsState(if (enabled) colors.moduleEnabled else Color.Transparent, spring())
    val pressed by animateColorAsState(if (isEdited && pressedIn) colors.inset.copy(alpha = colors.inset.alpha / 2) else Color.Transparent, spring())

    val revealed = Reveal.module === module && Reveal.setting == null
    val lit by animateColorAsState(if (revealed) colors.onSurface.copy(alpha = 0.4f) else Color.Transparent, tween(250))
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(revealed) {
        if (revealed) intoView.bringIntoView()
    }

    Tooltip(module.description, Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (panel == null) Modifier
                    else Modifier.onGloballyPositioned { panel.row = Rect(it.positionInRoot(), it.size.toSize()) }
                )
                .bringIntoViewRequester(intoView)
                .mouseClickable(onRightClick = onEdit) { if (module.alwaysEnabled) onEdit() else module.toggle() }
                .background(fill)
                .background(pressed)
                .background(lit)
                .hoverHighlight(colors.raised)
                .then(if (Look.emboss) Modifier.background(EMBOSS) else Modifier)
                .heightIn(min = Look.rowHeight)
                .padding(horizontal = Look.modulePadding)
        ) {
            SlidingText(
                text = module.name,
                color = when {
                    enabled -> colors.onModuleEnabled
                    isEdited -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                style = Type.body.shadowed(),
            )
        }
    }
}
