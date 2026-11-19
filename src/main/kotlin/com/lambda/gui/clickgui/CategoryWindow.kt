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
import com.lambda.gui.Snap
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.SlidingText
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.mouseDraggable
import com.lambda.gui.component.window
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.gui.theme.shadowed
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import com.lambda.module.modules.client.Style
import kotlin.math.absoluteValue

internal val CategoryWindowWidth get() = Look.windowWidth
internal val CategoryWindowGap get() = Look.windowGap

/** Room between the edge of a window and the text of its rows */
private val RowPadding get() = Look.modulePadding

/** How much of a window is always kept on the screen from its top: its header and a module or two */
private val Reach = 40.dp

/** How far from its place in the row, in dp either way, a window may be left and still count as in it */
private const val InRow = 1.5f

/**
 * What makes a module a tile rather than a flat stripe, when the style asks for it: a trace of light along the top that is gone
 * by the bottom, over whatever the tile is filled with. Only just there: it is felt more than seen.
 * The tiles touch, as a gap would show the dark panel as a black line between two enabled ones.
 */
private val Emboss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent))

/**
 * A draggable window listing the modules of [tag]. Left click on a module toggles it, right click
 * opens its settings; right click on the header folds the window away. The settings are a panel
 * beside the module, one of [panels], unless the style has them [inline], under the module.
 *
 * Until it has been dragged somewhere it sits at [defaultPosition]. It goes exactly where it is
 * dragged, unless [snapping], when it lines up with the screen, the grid and the other windows,
 * whose places are kept in [bounds]. Beside another window it snaps to the distance they have
 * in their row, so one that was moved by accident can be put straight back.
 */
@Composable
internal fun CategoryWindow(
    tag: ModuleTag,
    defaultPosition: DpOffset,
    bounds: MutableMap<ModuleTag, Rect>,
    snapping: Boolean,
    edited: List<Module>,
    panels: List<SettingsPanelState>,
    inline: ((Module) -> SettingsPanelState)?,
    onEdit: (Module) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modules by observe { ModuleRegistry.modules.filter { it.tag == tag && it.showInClickGui.value } }
    if (modules.isEmpty()) return

    val colors = LambdaTheme.colors
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize.toSize()
    val screenHeight = with(density) { screen.height.toDp() }

    // Where the window was left and whether it is folded away live in the config alone,
    // so resetting the layout there is all it takes to put the window back
    val placement by observe { GuiConfig.windows[tag.name] }
    val position = placement?.takeUnless { it.inRow }?.let { DpOffset(it.x.dp, it.y.dp) }
    val collapsed = placement?.collapsed ?: false

    // Where the pointer has taken the window's corner while it is being dragged, in pixels.
    // Where the window is shown, and left, follows from it and from whether it snaps right now.
    var dragged by remember { mutableStateOf<Offset?>(null) }
    val shownPosition = dragged?.let { corner ->
        val window = Rect(corner, bounds[tag]?.size ?: Size.Zero)
        val placed =
            if (snapping) Snap.snap(window, bounds.filterKeys { it != tag }.values, screen, density, with(density) { CategoryWindowGap.toPx() })
            else Snap.free(window, screen)
        with(density) { DpOffset(placed.position.x.toDp(), placed.position.y.toDp()) }
    } ?: (position ?: defaultPosition).let { at ->
        // Never off the screen, wherever it was left: a screen made smaller since, by a larger
        // scale or a smaller window, would otherwise put it out of reach
        val screenWidth = with(density) { screen.width.toDp() }
        DpOffset(
            at.x.coerceIn(0.dp, (screenWidth - CategoryWindowWidth).coerceAtLeast(0.dp)),
            at.y.coerceIn(0.dp, (screenHeight - Reach).coerceAtLeast(0.dp)),
        )
    }
    val currentPosition by rememberUpdatedState(shownPosition)
    DisposableEffect(tag) {
        onDispose { bounds -= tag }
    }

    // A window left in its place in the row, or put back there, is kept as being in the row
    // rather than at the spot: it then follows the row when the style spaces, sizes or moves it,
    // where a spot would pin it under the windows that do
    fun place(at: DpOffset, collapsed: Boolean) {
        val inRow = (at.x - defaultPosition.x).value.absoluteValue < InRow && (at.y - defaultPosition.y).value.absoluteValue < InRow
        GuiConfig.windows += tag.name to
            if (inRow) GuiConfig.WindowPlacement.inRow(collapsed)
            else GuiConfig.WindowPlacement(at.x.value, at.y.value, collapsed)
    }
    // One that comes as a spot from elsewhere, a config saved before that distinction was made
    // or loaded just now, is looked at the same way as it is found
    LaunchedEffect(placement) {
        position?.let { place(it, collapsed) }
    }

    Column(
        modifier = modifier
            .offset(shownPosition.x, shownPosition.y)
            .width(CategoryWindowWidth)
            .onGloballyPositioned { bounds[tag] = Rect(it.positionInRoot(), it.size.toSize()) }
            .pointerInput(Unit) {
                // Seen on the way down to the children, so any press inside raises the window
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onFocus()
                    }
                }
            }
            .window(colors.surface, colors.border, RoundedCornerShape(Radius.Window))
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
                    },
                ) { delta -> dragged = dragged?.plus(delta) }
                .mouseClickable(onRightClick = {
                    // Settings hang off a row of the list, and go with it
                    if (!collapsed) edited.filter { it in modules }.forEach(onEdit)
                    place(currentPosition, !collapsed)
                })
                .padding(horizontal = RowPadding, vertical = 2.dp)
        )

        // Unfolds out of its header each time the GUI is opened as well as when it is unfolded
        // by hand. Closing the GUI takes it away at once: the list starts over, folded, unseen.
        val shown = LocalShown.current
        val unfolded = remember(shown) { MutableTransitionState(false) }
        unfolded.targetState = shown && !collapsed
        // Settings hanging off a row wait for the rows to be where they will stay
        val settled = unfolded.isIdle && unfolded.currentState
        // Looked up here, where a panel being added to the list has this run again to tell it
        val hanging = panels.filter { it.module in modules }
        SideEffect { hanging.forEach { it.settled = settled } }
        AnimatedVisibility(
            visibleState = unfolded,
            enter = expandVertically(Motion.windows),
            exit = shrinkVertically(Motion.windows),
        ) {
            // Never taller than the room left under the header, however far down the window sits
            ScrollColumn(Modifier.heightIn(max = (screenHeight - shownPosition.y - 24.dp).coerceAtLeast(48.dp))) {
                modules.forEach { module ->
                    ModuleRow(
                        module = module,
                        isEdited = module in edited,
                        // Settings under the module are plainly its own: it is only pressed in
                        // for ones that are out beside it, to show which row they hang off
                        pressedIn = inline == null,
                        panel = panels.find { it.module == module },
                        onEdit = { onEdit(module) },
                    )
                    // Where the style has settings drop down under their module instead
                    if (inline != null) InlineSettings(inline(module), open = module in edited)
                }
            }
        }
    }
}

/** One module of the list. While its settings [panel] is on screen the row tells it where it is. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleRow(module: Module, isEdited: Boolean, pressedIn: Boolean, panel: SettingsPanelState?, onEdit: () -> Unit) {
    val colors = LambdaTheme.colors
    val enabled by observe { module.isEnabled }
    val embossed by observe { Style.emboss }
    val fill by animateColorAsState(if (enabled) colors.moduleEnabled else Color.Transparent, spring())
    // Pressed in like a button that is held down, for as long as its settings are out
    val pressed by animateColorAsState(if (isEdited && pressedIn) colors.inset.copy(alpha = colors.inset.alpha / 2) else Color.Transparent, spring())

    // Lit and brought into view while it is what the search points at
    val revealed = Reveal.module === module && Reveal.setting == null
    // In the colour of text, white on the dark theme: nothing else in the interface is filled with it
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
                // One that is always enabled has nothing to toggle: either click opens its settings
                .mouseClickable(onRightClick = onEdit) { if (module.alwaysEnabled) onEdit() else module.toggle() }
                .background(fill)
                .background(pressed)
                .background(lit)
                // A shade lighter under the pointer, or darker in the light theme
                .hoverHighlight(colors.raised)
                .then(if (embossed) Modifier.background(Emboss) else Modifier)
                // As tall as the style asks, or as its name needs if that is more
                .heightIn(min = RowHeight)
                .padding(horizontal = RowPadding)
        ) {
            // A name too long for the window slides along rather than being cut short
            SlidingText(
                text = module.name,
                // The fill is for what is on: one that is only being edited is merely lit
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
