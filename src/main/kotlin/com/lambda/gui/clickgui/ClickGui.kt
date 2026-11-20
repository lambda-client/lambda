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
import com.lambda.gui.Reveal
import com.lambda.gui.Snap
import com.lambda.gui.Snap.snapLines
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
import com.lambda.module.ModuleTag
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Style

internal val CLICK_GUI_CATEGORIES = ModuleTag.defaults - ModuleTag.HUD

private class RowLayout(var row: Triple<Dp, Dp, Dp>, var shown: Boolean = false)

internal sealed interface ClickGuiDialog {
    data object About : ClickGuiDialog
    data object NewAutomationConfig : ClickGuiDialog
    data class Settings(val config: Config) : ClickGuiDialog
}

internal object ClickGuiState {
    var dialog: ClickGuiDialog? by mutableStateOf(null)
    var developerMode by mutableStateOf(false)
    var focusSearch by mutableStateOf(false)
}

@Composable
internal fun ClickGui() {
    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize
    val developerMode = ClickGuiState.developerMode
    val tags by observe {
        CLICK_GUI_CATEGORIES.filter(ModuleTag::isTagShown) + listOfNotNull(ModuleTag.DEBUG.takeIf { developerMode })
    }

    val settings = remember { OpenSettings() }
    val inline by observe { Style.inlineSettings }

    val revealedModule = Reveal.module
    val revealedSetting = Reveal.setting
    LaunchedEffect(revealedModule, revealedSetting) {
        if (revealedModule != null && revealedModule !is HudModule && revealedSetting != null) settings.open(revealedModule)
    }

    val shown = LocalShown.current
    LaunchedEffect(shown) {
        if (!shown && !GuiConfig.keepSettingsOpen) settings.forget()
    }

    val row = Triple(Look.windowWidth, Look.windowGap, Look.windowTop)
    val laidOut = remember { RowLayout(row) }
    SideEffect {
        if (row != laidOut.row && shown && laidOut.shown) {
            GuiConfig.windows = GuiConfig.windows.mapValues { GuiConfig.WindowPlacement.inRow(it.value.collapsed) }
        }
        laidOut.row = row
        laidOut.shown = shown
    }

    val stacking = remember { mutableStateListOf<ModuleTag>() }
    val bounds = remember { mutableStateMapOf<ModuleTag, Rect>() }
    val snapping by observe { Snap.isActive }
    var drop by remember { mutableStateOf<Snap.Result?>(null) }

    Box(Modifier.fillMaxSize().mouseClickable(keyboard = false, onClick = settings::closeAll).snapLines { drop }) {
        val inRow = tags.count { it != ModuleTag.DEBUG }
        val screenWidth = with(density) { screen.width.toDp() }
        val pitch =
            (Look.windowWidth + Look.windowGap)
                .coerceAtMost((screenWidth - Look.windowWidth) / (inRow - 1).coerceAtLeast(1))
                .coerceAtLeast(0.dp)
        val rowWidth = Look.windowWidth + pitch * (inRow - 1)
        val rowStart = ((screenWidth - rowWidth) / 2).coerceAtLeast(0.dp)

        tags.forEachIndexed { index, tag ->
            key(tag) {
                CategoryWindow(
                    tag = tag,
                    defaultPosition = DpOffset(rowStart + pitch * index, Look.windowTop),
                    bounds = bounds,
                    snapping = snapping,
                    onDrop = { drop = it },
                    settings = settings,
                    inline = inline,
                    onFocus = {
                        stacking.remove(tag)
                        stacking.add(tag)
                    },
                    modifier = Modifier.zIndex(stacking.indexOf(tag).toFloat())
                )
            }
        }

        settings.panels.forEach { panel ->
            key(panel.module) {
                SettingsPanel(
                    panel = panel,
                    open = settings.isOpen(panel.module) && !inline,
                    windows = bounds,
                    onClosed = { settings.closed(panel.module) },
                    modifier = Modifier.zIndex(stacking.size.toFloat())
                )
            }
        }

        MenuBar(
            Modifier
                .align(Alignment.TopStart)
                .zIndex(stacking.size + 1f)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) settings.closeAll()
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

    val prompt = observe { AutoUpdater.prompt }.value
    val closeDialog = { ClickGuiState.dialog = null }
    val dialog = ClickGuiState.dialog
    when {
        prompt != null -> LoaderPromptDialog(prompt)
        dialog is ClickGuiDialog.About -> AboutDialog(closeDialog)
        dialog is ClickGuiDialog.NewAutomationConfig -> NewAutomationConfigDialog(closeDialog)
        dialog is ClickGuiDialog.Settings -> ConfigDialog(dialog.config, closeDialog)
    }
}
