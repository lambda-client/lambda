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

package com.lambda.gui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import com.lambda.config.Config
import com.lambda.config.settings.complex.Bind
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.gui.Snap
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.Button
import com.lambda.gui.component.ContextMenu
import com.lambda.gui.component.MenuDivider
import com.lambda.gui.component.MenuHelp
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuLabel
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.onMousePress
import com.lambda.gui.component.rememberLast
import com.lambda.gui.component.window
import com.lambda.gui.core.observe
import com.lambda.gui.settings.SettingsList
import com.lambda.gui.theme.Hairline
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.SoftTextShadow
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.gui.theme.toCompose
import com.lambda.module.HudModule
import kotlin.math.max

/**
 * What the editor knows while it is open: where the elements are and what is happening to them.
 *
 * @param snapping whether a dragged element snaps right now, which changes mid-drag as the snap
 * key is pressed and let go
 */
internal class HudEditorState(snapping: State<Boolean>) {
    val snapping by snapping

    /** Where every element currently is, in scene pixels: what a dragged element snaps against */
    val bounds = mutableStateMapOf<HudModule, Rect>()

    /** The element being dragged, and where the pointer has taken its top left corner */
    var dragged by mutableStateOf<HudModule?>(null)
    var draggedTo by mutableStateOf(Offset.Zero)

    /** The context menu that is open: where, and on which element, or null for the empty screen */
    var menu by mutableStateOf<Pair<Offset, HudModule?>?>(null)
        private set

    /** How many menus have been opened: each is a menu of its own, which unfolds anew */
    var menusOpened by mutableStateOf(0)
        private set

    fun openMenu(position: Offset, hud: HudModule?) {
        menu = position to hud
        menusOpened++
    }

    fun closeMenu() {
        menu = null
    }

    /** The config whose settings panel is open: an element's, or the HUD's own */
    var configuring by mutableStateOf<Config?>(null)
        private set

    /** Where the top left corner of that panel is, in scene pixels: where it opened, until it is dragged */
    var panelPosition by mutableStateOf(Offset.Zero)

    /** What the panel covers of the screen, kept current by the panel itself */
    var panelBounds = Rect.Zero

    /** Opens the settings of [config] at [position], or near the top of the screen's middle */
    fun configure(config: Config?, position: Offset? = null) {
        configuring = config
        if (config != null) panelPosition = position ?: Offset(screen.width / 2 - 120 * density.density, screen.height * 0.15f)
    }

    /** The screen the elements are laid out in: its size in scene pixels, and how many of those make a dp */
    var screen = Size.Zero
    var density = Density(1f)

    /**
     * Where the dragged element belongs: where the pointer took it, snapped if [snapping]. It is
     * shown there for as long as the drag lasts, overriding its saved anchor, and left there after.
     */
    val drop: Snap.Result? by derivedStateOf {
        val hud = dragged ?: return@derivedStateOf null
        val element = Rect(draggedTo, bounds[hud]?.size ?: return@derivedStateOf null)
        if (this.snapping) Snap.snap(element, bounds.filterKeys { it != hud }.values, screen, density)
        else Snap.free(element, screen)
    }

    fun shownPosition(hud: HudModule) = drop?.position.takeIf { dragged == hud }
}

/**
 * The HUD editor: the HUD as it appears in game, with every element draggable. A right click
 * on an element removes or configures it, and one on the empty screen adds the ones not shown.
 */
@Composable
internal fun HudEditor() {
    val snapping = observe { Snap.isActive }
    val editor = remember { HudEditorState(snapping) }
    editor.screen = LocalWindowInfo.current.containerSize.toSize()
    editor.density = LocalDensity.current

    // A setting the search points at is shown in the settings of its element
    val revealedElement = Reveal.module as? HudModule
    val revealedSetting = Reveal.setting
    LaunchedEffect(revealedElement, revealedSetting) {
        if (revealedElement != null && revealedSetting != null) editor.configure(revealedElement)
    }

    val showSnapLines by observe { GuiConfig.showSnapLines }
    val snapLineColor by observe { GuiConfig.snapLineColor.toCompose() }

    Box(
        Modifier
            .fillMaxSize()
            // Elements take the presses they handle, so one that arrives here was on the empty screen
            .onMousePress(PointerButton.Secondary) { editor.openMenu(it, null) }
            .pointerInput(Unit) {
                // Seen on the way down, before whatever was pressed: a press off the settings
                // panel closes it, and then does what it would have done anyway
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press || editor.configuring == null) continue
                        if (event.changes.first().position !in editor.panelBounds) editor.configure(null)
                    }
                }
            }
            .drawWithContent {
                val guides = editor.drop?.guides.takeIf { showSnapLines }
                if (guides != null && editor.snapping) drawGrid()
                drawContent()
                if (guides != null) drawGuides(guides, snapLineColor)
            }
    ) {
        HudLayer(editor)
        // Beside the HUD rather than over all of it, so a change shows on the element as it is made
        // Still there while it shrinks away after it was closed
        rememberLast(editor.configuring)?.let { config -> key(config) { SettingsPanel(config, editor.configuring === config, editor) } }
    }

    // Still there while the menu folds away after it was closed
    rememberLast(editor.menu)?.let { (position, hud) ->
        // One menu per right click, so a click elsewhere with a menu open unfolds the new one
        key(editor.menusOpened) {
            ContextMenu(editor.menu != null, position.round(), onDismiss = editor::closeMenu) {
                if (hud != null) ElementMenu(hud, editor) else ScreenMenu(position, editor)
            }
        }
    }
}

@Composable
private fun ElementMenu(hud: HudModule, editor: HudEditorState) {
    MenuLabel(hud.name)
    MenuItem("Settings...") { editor.configure(hud, editor.menu?.first) }
    MenuItem("Remove") { hud.disable() }
    MenuDivider()
    EditorHelp()
}

/** How the editor is used, told by a menu entry to whoever rests the pointer on it */
@Composable
private fun EditorHelp() {
    val snapping by observe {
        when {
            !GuiConfig.snapEnabled -> ""
            GuiConfig.snapKey == Bind.EMPTY -> " Elements snap into line."
            else -> " Hold ${GuiConfig.snapKey.name} to snap it into line."
        }
    }
    MenuHelp("Drag an element to move it.$snapping Right click an element to remove or configure it, or the empty screen to add one.")
}

@Composable
private fun ScreenMenu(position: Offset, editor: HudEditorState) {
    val screen = LocalWindowInfo.current.containerSize
    val hidden by observe { Hud.modules.filter { !it.isEnabled }.sortedBy { it.name.lowercase() } }
    if (hidden.isEmpty()) MenuLabel("Every HUD element is shown")
    else {
        MenuLabel("Add HUD Element")
        hidden.forEach { hud ->
            MenuItem(hud.name) {
                // New elements appear where the menu was opened
                hud.anchorX = (position.x / screen.width).coerceIn(0f, 1f)
                hud.anchorY = (position.y / screen.height).coerceIn(0f, 1f)
                hud.enable()
            }
        }
    }
    MenuDivider()
    MenuItem("HUD Settings...") { editor.configure(HudConfig, position) }
    EditorHelp()
    MenuItem("Back") { LambdaGui.back() }
}

/**
 * The settings of [config] in a panel over the editor, which leaves the HUD in view and in
 * reach: an element can be watched changing, and moved, with its settings open. The panel is
 * dragged by its title, and stays on the screen.
 */
@Composable
private fun SettingsPanel(config: Config, open: Boolean, editor: HudEditorState) {
    val colors = LambdaTheme.colors
    val density = LocalDensity.current
    val maxHeight = with(density) { editor.screen.height.toDp() } * 0.6f
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Kept on the screen, wherever it was opened or dragged to
    val room = Offset((editor.screen.width - size.width).coerceAtLeast(0f), (editor.screen.height - size.height).coerceAtLeast(0f))
    val position = Offset(editor.panelPosition.x.coerceIn(0f, room.x), editor.panelPosition.y.coerceIn(0f, room.y))
    val shownAt by rememberUpdatedState(position)
    if (open) editor.panelBounds = Rect(position, size.toSize())
    // Escape closes the panel before it leaves the editor
    DisposableEffect(Unit) {
        LambdaGui.onEscape = { (editor.configuring != null).also { editor.configure(null) } }
        onDispose { LambdaGui.onEscape = null }
    }

    // Grows out of its middle and shrinks back into it. By size alone: a fade would draw it
    // into a layer of its own, with nothing behind the glass for it to blur.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    AnimatedVisibility(
        visibleState = visibility,
        modifier = Modifier.offset { position.round() },
        enter = scaleIn(PanelScale),
        exit = scaleOut(PanelScale),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .onSizeChanged { size = it }
                .window(colors.panel, colors.border, RoundedCornerShape(Radius.Large), floating = true)
                // Dragged by any part of it that is not a control: those take their presses first
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        editor.panelPosition = shownAt
                        drag(down.id) { change ->
                            editor.panelPosition += change.positionChange()
                            change.consume()
                        }
                    }
                }
                .padding(6.dp)
                .width(200.dp)
        ) {
            Text("${config.name} Settings", Modifier.fillMaxWidth().padding(horizontal = 4.dp), style = Type.title)
            ScrollColumn(Modifier.heightIn(max = maxHeight)) {
                SoftTextShadow { SettingsList(config.settingLayers) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1f))
                Button("Reset") { config.resetSettings() }
                Button("Close") { editor.configure(null) }
            }
        }
    }
}

private val PanelScale = spring<Float>(stiffness = Spring.StiffnessMedium)

/**
 * Makes a HUD element movable: a left drag moves it and saves where it ends up, a right click
 * opens its menu. An outline shows how far the element reaches, brighter on the one being moved
 * and on one the search points at.
 */
internal fun Modifier.hudEditable(hud: HudModule, editor: HudEditorState): Modifier = this
    .onGloballyPositioned { editor.bounds[hud] = Rect(it.positionInRoot(), it.size.toSize()) }
    .onMousePress(PointerButton.Secondary) { position ->
        editor.bounds[hud]?.let { editor.openMenu(it.topLeft + position, hud) }
    }
    .pointerInput(hud) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val origin = editor.bounds[hud] ?: return@awaitEachGesture
            down.consume()

            editor.draggedTo = origin.topLeft
            editor.dragged = hud
            try {
                drag(down.id) { change ->
                    editor.draggedTo += change.positionChange()
                    change.consume()
                }
            } finally {
                // Saved as the fraction of the free room, so the element keeps to its edge when the window is resized
                val dropped = editor.drop?.position ?: origin.topLeft
                val room = editor.screen - origin.size
                hud.anchorX = if (room.width > 0f) (dropped.x / room.width).coerceIn(0f, 1f) else 0f
                hud.anchorY = if (room.height > 0f) (dropped.y / room.height).coerceIn(0f, 1f) else 0f
                editor.dragged = null
            }
        }
    }
    .drawBehind {
        // Half of the element's padding, so the outline never touches what it is drawn around
        val width = Hairline.toPx()
        drawRect(
            color = Color.White.copy(alpha = if (editor.dragged == hud || Reveal.module === hud) 0.9f else 0.35f),
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width)
        )
    }

/** The snapping grid, behind the elements while one of them is being snapped */
private fun DrawScope.drawGrid() {
    if (!GuiConfig.snapToGrid) return
    val step = max(4f, GuiConfig.gridSize.dp.toPx())
    val color = Color.White.copy(alpha = 0.11f)
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), Stroke.HairlineWidth)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), Stroke.HairlineWidth)
        y += step
    }
}

/** The lines the dragged element has snapped to, a single pixel wide whatever the screen */
private fun DrawScope.drawGuides(guides: Snap.Guides, color: Color) {
    guides.x?.let { drawLine(color, Offset(it, 0f), Offset(it, size.height), Stroke.HairlineWidth) }
    guides.y?.let { drawLine(color, Offset(0f, it), Offset(size.width, it), Stroke.HairlineWidth) }
}

private operator fun Size.minus(other: Size) = Size(width - other.width, height - other.height)
