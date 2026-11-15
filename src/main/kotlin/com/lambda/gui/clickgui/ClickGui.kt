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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.ModuleTag

/** How far from the top of the screen the category windows are lined up until they are moved: clear of the buttons, with room to drag them up */
private val WindowRowTop = 94.dp

/**
 * The categories the click GUI has a window for. HUD elements are not among them: they are
 * added, removed and set up where they are arranged, in the HUD editor.
 */
internal val ClickGuiCategories = ModuleTag.defaults - ModuleTag.HUD

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

    // The module whose settings are open. Its panel stays in the list until it has folded away,
    // so clicking from module to module leaves several on screen, each finishing its own animation
    var edited by remember { mutableStateOf<Module?>(null) }
    val panels = remember { mutableStateListOf<SettingsPanelState>() }
    // Every panel that has been open, so one that is opened again is as tall as it was left
    val panelStates = remember { HashMap<Module, SettingsPanelState>() }
    fun edit(module: Module) {
        edited = module
        val panel = panelStates.getOrPut(module) { SettingsPanelState(module) }
        if (panel !in panels) panels += panel
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
            edited = null
            panels.clear()
        }
    }

    // Most recently clicked last, so it is drawn on top
    val stacking = remember { mutableStateListOf<ModuleTag>() }
    // Where every window is, in scene pixels: what a dragged one snaps against while the snap key is held
    val bounds = remember { mutableStateMapOf<ModuleTag, Rect>() }
    val snapping by observe { Snap.isActive }

    Box(Modifier.fillMaxSize().mouseClickable { edited = null }) {
        // The row is centred without the debug window, which takes the place after its end:
        // turning developer mode on or off leaves every other window where it is
        val inRow = tags.count { it != ModuleTag.DEBUG }
        val rowWidth = CategoryWindowWidth * inRow + CategoryWindowGap * (inRow - 1)
        val rowStart = (with(density) { screen.width.toDp() } - rowWidth) / 2

        tags.forEachIndexed { index, tag ->
            key(tag) {
                CategoryWindow(
                    tag = tag,
                    defaultPosition = DpOffset(rowStart + (CategoryWindowWidth + CategoryWindowGap) * index, WindowRowTop),
                    bounds = bounds,
                    snapping = snapping,
                    edited = edited,
                    panels = panels,
                    onEdit = { module -> if (module == edited) edited = null else edit(module) },
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
                    open = panel.module == edited,
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
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) edited = null
                        }
                    }
                }
        )
        Button(
            label = "HUD Editor",
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).zIndex(stacking.size + 1f)
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
