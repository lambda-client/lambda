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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lambda.config.Config
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.gui.Snap
import com.lambda.gui.component.Button
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.observe
import com.lambda.gui.menu.AboutDialog
import com.lambda.gui.menu.ConfigDialog
import com.lambda.gui.menu.LoaderPromptDialog
import com.lambda.gui.menu.MenuBar
import com.lambda.gui.menu.NewAutomationConfigDialog
import com.lambda.gui.theme.Look
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Style

/** How far from the top of the screen the category windows are lined up until they are moved: clear of the buttons, with room to drag them up */
private val WindowRowTop get() = Look.windowTop

/**
 * The categories the click GUI has a window for. HUD elements are not among them: they are
 * added, removed and set up where they are arranged, in the HUD editor.
 */
internal val ClickGuiCategories = ModuleTag.defaults - ModuleTag.HUD

/** What the row of windows was last laid out with, and whether the interface was open then */
private class RowLayout(var row: Triple<Dp, Dp, Dp>, var shown: Boolean = false)

/** A window the click GUI shows over everything else until it is dismissed */
internal sealed interface ClickGuiDialog {
    data object About : ClickGuiDialog
    data object NewAutomationConfig : ClickGuiDialog
    data class Settings(val config: Config) : ClickGuiDialog
}

/** What is open in the click GUI. Lives outside the composition so a keybind can open the search. */
internal object ClickGuiState {
    var dialog: ClickGuiDialog? by mutableStateOf(null)
    var developerMode by mutableStateOf(false)

    /** Set to have the search field of the menu bar take the keyboard, which then unsets it */
    var focusSearch by mutableStateOf(false)
}

/**
 * The click GUI: a window of modules for every shown category, the settings of whichever
 * module was last right-clicked, popped out beside it, a menu bar along the top for everything
 * else, and the way to the HUD editor in the corner.
 */
@Composable
internal fun ClickGui() {
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize
    val developerMode = ClickGuiState.developerMode
    val tags by observe {
        ClickGuiCategories.filter(ModuleTag::isTagShown) + listOfNotNull(ModuleTag.DEBUG.takeIf { developerMode })
    }

    // The modules whose settings are open, the last opened last: one of them, unless the style
    // lets several be open at once. A panel stays in the list until it has folded away, so
    // clicking from module to module leaves several on screen, each finishing its own animation
    val edited = remember { mutableStateListOf<Module>() }
    val panels = remember { mutableStateListOf<SettingsPanelState>() }
    // Every panel that has been open, so one that is opened again is as tall as it was left
    val panelStates = remember { HashMap<Module, SettingsPanelState>() }
    fun stateOf(module: Module) = panelStates.getOrPut(module) { SettingsPanelState(module) }
    // Beside the module as a panel, or where the style asks for it, dropped down under it
    val inline by observe { Style.inlineSettings }
    val several by observe { Style.multipleSettings }
    fun edit(module: Module) {
        if (!several) edited.clear()
        if (module !in edited) edited += module
        val panel = stateOf(module)
        if (!inline && panel !in panels) panels += panel
    }
    // What a press beside the settings does: puts them away, all of them where several are open
    fun dismiss() = edited.clear()
    // The style changed with settings open: held to one at a time again, the last opened is the
    // one that stays, and no longer under their modules, they are out beside them as panels
    LaunchedEffect(inline, several) {
        if (!several && edited.size > 1) edited.removeRange(0, edited.size - 1)
        if (!inline) edited.forEach { module -> stateOf(module).let { if (it !in panels) panels += it } }
    }

    // A setting the search points at is shown in the settings of its module
    val revealedModule = Reveal.module
    val revealedSetting = Reveal.setting
    LaunchedEffect(revealedModule, revealedSetting) {
        if (revealedModule != null && revealedModule !is HudModule && revealedSetting != null) edit(revealedModule)
    }

    // Put away, the click GUI closes the settings that were open, unless they are to be kept
    val shown = LocalShown.current
    LaunchedEffect(shown) {
        if (!shown && !GuiConfig.keepSettingsOpen) {
            edited.clear()
            panels.clear()
        }
    }

    // The row is laid out anew when the style changes what it is made of, the width of a window,
    // the gap between two or how far down they start: every window goes back to its place in it.
    // A row spaced or sized differently around windows left where they were would not be one, and
    // which of those were meant to be elsewhere is not something their places can tell. Only while
    // the interface is open, which is when the style is being set: what it was changed to in the
    // meantime, by a config being loaded, moves nothing.
    val row = Triple(CategoryWindowWidth, CategoryWindowGap, WindowRowTop)
    val laidOut = remember { RowLayout(row) }
    SideEffect {
        if (row != laidOut.row && shown && laidOut.shown) {
            GuiConfig.windows = GuiConfig.windows.mapValues { GuiConfig.WindowPlacement.inRow(it.value.collapsed) }
        }
        laidOut.row = row
        laidOut.shown = shown
    }

    // Most recently clicked last, so it is drawn on top
    val stacking = remember { mutableStateListOf<ModuleTag>() }
    // Where every window is, in scene pixels: what a dragged one snaps against while the snap key is held
    val bounds = remember { mutableStateMapOf<ModuleTag, Rect>() }
    val snapping by observe { Snap.isActive }

    Box(Modifier.fillMaxSize().mouseClickable(keyboard = false, onClick = ::dismiss)) {
        // The row is centred without the debug window, which takes the place after its end:
        // turning developer mode on or off leaves every other window where it is
        val inRow = tags.count { it != ModuleTag.DEBUG }
        // On a screen too narrow for the row, which a large scale makes of any screen, the windows
        // close up and overlap like a hand of cards rather than run off its edge: every one of
        // them keeps a strip that can be pressed, which brings it to the front
        val screenWidth = with(density) { screen.width.toDp() }
        val pitch = (CategoryWindowWidth + CategoryWindowGap)
            .coerceAtMost((screenWidth - CategoryWindowWidth) / (inRow - 1).coerceAtLeast(1))
            .coerceAtLeast(0.dp)
        val rowWidth = CategoryWindowWidth + pitch * (inRow - 1)
        val rowStart = ((screenWidth - rowWidth) / 2).coerceAtLeast(0.dp)

        tags.forEachIndexed { index, tag ->
            key(tag) {
                CategoryWindow(
                    tag = tag,
                    defaultPosition = DpOffset(rowStart + pitch * index, WindowRowTop),
                    bounds = bounds,
                    snapping = snapping,
                    edited = edited,
                    panels = panels,
                    inline = if (inline) ::stateOf else null,
                    onEdit = { module -> if (module in edited) edited -= module else edit(module) },
                    onFocus = {
                        stacking.remove(tag)
                        stacking.add(tag)
                    },
                    modifier = Modifier.zIndex(stacking.indexOf(tag).toFloat())
                )
            }
        }

        panels.forEach { panel ->
            key(panel.module) {
                SettingsPanel(
                    panel = panel,
                    // One left open when the style is changed to have them inline folds away
                    open = panel.module in edited && !inline,
                    onClosed = { panels -= panel },
                    modifier = Modifier.zIndex(stacking.size.toFloat())
                )
            }
        }

        MenuBar(
            Modifier
                .align(Alignment.TopStart)
                .zIndex(stacking.size + 1f)
                // A press anywhere on the bar puts the open settings away, as one on the empty screen does
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) dismiss()
                        }
                    }
                }
        )
        Button(
            label = "HUD Editor",
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).zIndex(stacking.size + 1f),
            prominent = true,
        ) { LambdaGui.open(LambdaGui.Screen.HudEditor) }
    }

    val prompt by observe { AutoUpdater.prompt }
    prompt?.let { LoaderPromptDialog(it) } ?: when (val dialog = ClickGuiState.dialog) {
        ClickGuiDialog.About -> AboutDialog(onDismiss = { ClickGuiState.dialog = null })
        ClickGuiDialog.NewAutomationConfig -> NewAutomationConfigDialog(onDismiss = { ClickGuiState.dialog = null })
        is ClickGuiDialog.Settings -> ConfigDialog(dialog.config, onDismiss = { ClickGuiState.dialog = null })
        null -> Unit
    }
}
