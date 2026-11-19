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

package com.lambda.gui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.Lambda.REPO_URL
import com.lambda.Lambda.mc
import com.lambda.config.ConfigLoader
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.gui.clickgui.ClickGuiCategories
import com.lambda.gui.clickgui.ClickGuiDialog
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.gui.component.Menu
import com.lambda.gui.component.MenuDivider
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuLabel
import com.lambda.gui.component.MenuState
import com.lambda.gui.component.Submenu
import com.lambda.gui.component.Suggestions
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.consumeClicks
import com.lambda.gui.component.glass
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.rememberLast
import com.lambda.gui.component.rememberTexture
import com.lambda.gui.core.observe
import com.lambda.gui.core.observeSafe
import com.lambda.gui.hud.HudConfig
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import com.lambda.network.LambdaAPI
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.Diagnostics.gatherDiagnostics
import com.lambda.util.FolderRegistry
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.gui.screen.DebugOptionsScreen
import net.minecraft.network.packet.c2s.play.ChangeGameModeC2SPacket
import net.minecraft.server.command.GameModeCommand
import net.minecraft.util.Util
import net.minecraft.world.GameMode
import java.nio.file.Path
import java.util.*

internal val MenuBarHeight = 15.dp

/**
 * The strip across the top of the click GUI. Everything that is not a module is in one of its
 * menus, and its search field finds modules and settings and shows where they are.
 */
@Composable
internal fun MenuBar(modifier: Modifier = Modifier) {
    val colors = LambdaTheme.colors
    // The search field is in the middle of the bar for as long as the menus leave the middle
    // free. On a bar too short for that, a large scale on a narrow screen, it moves over just
    // far enough to clear them.
    var bar by remember { mutableStateOf(IntSize.Zero) }
    var menus by remember { mutableStateOf(IntSize.Zero) }
    var search by remember { mutableStateOf(IntSize.Zero) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(MenuBarHeight)
            .onSizeChanged { bar = it }
            // Glass like a window, but running from edge to edge it needs no outline
            .glass(colors.surface, RectangleShape)
            .consumeClicks()
    ) {
        Row(Modifier.align(Alignment.CenterStart).fillMaxHeight().onSizeChanged { menus = it }, verticalAlignment = Alignment.CenterVertically) {
            BarMenu("Lambda", icon = "textures/lambda.png") { LambdaMenu() }
            BarMenu("Modules") { ModulesMenu() }
            BarMenu("Automation") { AutomationMenu() }
            BarMenu("Minecraft") { MinecraftMenu() }
            BarMenu("Help") { HelpMenu() }
        }
        SearchField(
            Modifier
                .onSizeChanged { search = it }
                .offset { IntOffset((menus.width - (bar.width - search.width) / 2).coerceAtLeast(0), 0) }
        )
    }
}

/** An entry of the bar that drops a menu down underneath itself, with the texture at [icon] before its [label] if it has one */
@Composable
private fun BarMenu(label: String, icon: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val menu = remember { MenuState() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .hoverHighlight(LambdaTheme.colors.controlSelected)
            .mouseClickable(onClick = menu::toggle)
            .padding(horizontal = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // Drawn far smaller than it is made, which only looks clean when it is filtered well
            if (icon != null) Image(rememberTexture(icon), contentDescription = null, Modifier.size(IconSize), filterQuality = FilterQuality.High)
            Text(label, maxLines = 1)
        }
        // Beside the label and not after it in its row, where the menu, once open, would be
        // one more thing for the row to space out and the entry would grow by a gap
        Menu(menu.isOpen, onDismiss = menu::dismiss, content = content)
    }
}

/** Edge length of the logo in the bar: the height of a capital of the Minecraft font and a little over */
private val IconSize = 9.dp

/**
 * The search field of the bar. Results drop down under it as the query is typed, and one of
 * them is always marked, the first to begin with. The arrow keys, or Tab and Shift with Tab,
 * move the mark up and down the list and round its ends, and so does the pointer; Enter takes
 * the marked one, a click any, and Escape empties the field. Nothing in it needs a mouse:
 * Control with F puts the cursor in the field.
 */
@Composable
private fun SearchField(modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    // Control with F puts the cursor in the field from anywhere in the click GUI
    val field = remember { FocusRequester() }
    val asked = ClickGuiState.focusSearch
    LaunchedEffect(asked) {
        if (asked) field.requestFocus()
        ClickGuiState.focusSearch = false
    }
    var query by remember { mutableStateOf("") }
    // The results can be put away with a click elsewhere, and come back with the next keystroke
    var listed by remember { mutableStateOf(false) }
    val results = remember(query) { Search.find(query.trim()) }
    val open = listed && query.isNotBlank()
    // What was listed stays to be seen while the list folds away
    val shown = rememberLast(results.takeIf { open }).orEmpty()
    // Which result is marked. Every keystroke lists anew, with the best match first and marked.
    var marked by remember(results) { mutableStateOf(0) }

    fun choose(result: Search.Result) {
        query = ""
        listed = false
        focus.clearFocus()
        result.choose()
    }

    Box(
        modifier.onPreviewKeyEvent { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                // An empty field lets Escape through, to close the click GUI as it does anywhere else
                event.key == Key.Escape && query.isNotEmpty() -> {
                    query = ""
                    listed = false
                    true
                }
                event.key == Key.Enter -> {
                    results.getOrNull(marked)?.let(::choose)
                    true
                }
                // Without a list to move through, Tab is left to go where it goes anywhere else
                !open || results.isEmpty() -> false
                event.key == Key.DirectionDown || event.key == Key.Tab && !event.isShiftPressed -> {
                    marked = (marked + 1).mod(results.size)
                    true
                }
                event.key == Key.DirectionUp || event.key == Key.Tab -> {
                    marked = (marked - 1).mod(results.size)
                    true
                }
                else -> false
            }
        }
    ) {
        TextInput(
            value = query,
            onValueChange = {
                query = it
                listed = true
            },
            modifier = Modifier.width(SearchWidth).focusRequester(field),
            placeholder = "Search modules and settings",
        )
        Suggestions(open, onDismiss = { listed = false }) {
            if (shown.isEmpty()) MenuLabel("Nothing found")
            shown.forEachIndexed { index, result ->
                ResultRow(result, marked = index == marked, onPoint = { marked = index }) { choose(result) }
            }
        }
    }
}

/**
 * One result of the search. The [marked] one is lit and kept in view, and the light passes from
 * one to the next rather than jumping. Moving the pointer over a row marks it, [onPoint]; a
 * pointer that merely lies where the list happens to open does not, or it would take the mark
 * from the keyboard with every letter typed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(result: Search.Result, marked: Boolean, onPoint: () -> Unit, onClick: () -> Unit) {
    val colors = LambdaTheme.colors
    val lit by animateColorAsState(if (marked) colors.controlSelected else Color.Transparent, tween(90))
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(marked) {
        if (marked) intoView.bringIntoView()
    }
    val point by rememberUpdatedState(onPoint)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = SearchWidth)
            .bringIntoViewRequester(intoView)
            .background(lit)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent().type == PointerEventType.Move) point()
                    }
                }
            }
            .mouseClickable(keyboard = false, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(result.name, maxLines = 1)
        Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
        Text(result.origin, color = colors.onSurfaceVariant, style = Type.label, maxLines = 1)
    }
}

private val SearchWidth = 150.dp

@Composable
private fun LambdaMenu() {
    Submenu("Save Config") {
        MenuItem("Save All Configs") {
            ConfigLoader.configCategories.forEach { it.trySaveToFile(true) }
            GuiConfig.info("Saved ${ConfigLoader.configCategories.size} configuration files.")
        }
        ConfigLoader.configCategories.forEach { category ->
            MenuItem("Save ${category.name}") {
                category.trySaveToFile(true)
                GuiConfig.info("Saved ${category.name}")
            }
        }
    }
    Submenu("Load Config") {
        MenuItem("Load All Configs") {
            ConfigLoader.configCategories.forEach { it.tryLoadFromFile() }
            GuiConfig.info("Loaded ${ConfigLoader.configCategories.size} configuration files.")
        }
        ConfigLoader.configCategories.forEach { category ->
            MenuItem("Load ${category.name}") {
                category.tryLoadFromFile()
                GuiConfig.info("Loaded ${category.name}")
            }
        }
    }
    MenuDivider()
    Submenu("Settings") {
        MenuItem("GUI Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(GuiConfig) }
        MenuItem("HUD Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(HudConfig) }
        MenuItem("Lambda API Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(LambdaAPI) }
        MenuItem("Baritone Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(BaritoneHandler) }
        MenuDivider()
        MenuItem("Reset Window Layout") { GuiConfig.resetWindows() }
    }
    Submenu("Open Folder") {
        FolderItem("Lambda Folder", FolderRegistry.lambda)
        FolderItem("Config Folder", FolderRegistry.config)
        FolderItem("Packet Logs Folder", FolderRegistry.packetLogs)
        FolderItem("Replay Folder", FolderRegistry.replay)
        FolderItem("Cache Folder", FolderRegistry.cache)
        FolderItem("Capes Folder", FolderRegistry.capes)
        FolderItem("Structures Folder", FolderRegistry.structure)
        FolderItem("Maps Folder", FolderRegistry.maps)
    }
    MenuDivider()
    MenuItem("About...") { ClickGuiState.dialog = ClickGuiDialog.About }
    MenuItem("Developer Mode", checked = ClickGuiState.developerMode) {
        ClickGuiState.developerMode = !ClickGuiState.developerMode
    }
    MenuDivider()
    MenuItem("Close GUI", hint = "Esc") { LambdaGui.back() }
    MenuItem("Exit Client") { mc.scheduleStop() }
}

/** Which categories have a window, and which modules are listed in them */
@Composable
private fun ModulesMenu() {
    Submenu("Shown Categories") {
        ClickGuiCategories.forEach { tag ->
            val shown by observe { ModuleTag.isTagShown(tag) }
            MenuItem(tag.name, checked = shown) { ModuleTag.toggleTag(tag) }
        }
    }
    MenuDivider()
    ClickGuiCategories.forEach { tag ->
        Submenu(tag.name) {
            ModuleRegistry.modules.filter { it.tag == tag }.forEach { module ->
                val shown by observe { module.showInClickGui.value }
                MenuItem(module.name, checked = shown) { module.showInClickGui.value = !shown }
            }
        }
    }
}

@Composable
private fun AutomationMenu() {
    val configs by observe { UserAutomationCategory.configs.filterIsInstance<UserAutomationConfig>() }
    MenuItem("New Config...") { ClickGuiState.dialog = ClickGuiDialog.NewAutomationConfig }
    MenuDivider()
    configs.forEach { config ->
        MenuItem("${config.name}...") { ClickGuiState.dialog = ClickGuiDialog.Settings(config) }
    }
    MenuItem("${AutomationConfig.DEFAULT.name}...") { ClickGuiState.dialog = ClickGuiDialog.Settings(AutomationConfig.DEFAULT) }
}

@Composable
private fun MinecraftMenu() {
    val gameDirectory = mc.runDirectory.toPath().toAbsolutePath()
    Submenu("Open Folder") {
        FolderItem("Minecraft Folder", gameDirectory)
        FolderItem("Saves Folder", gameDirectory.resolve("saves"))
        FolderItem("Screenshots Folder", gameDirectory.resolve("screenshots"))
        FolderItem("Resource Packs Folder", gameDirectory.resolve("resourcepacks"))
        FolderItem("Mods Folder", gameDirectory.resolve("mods"))
    }
    MenuDivider()

    val gameMode by observeSafe { interaction.gameMode }
    val mayChangeGameMode by observeSafe { GameModeCommand.PERMISSION_CHECK.allows(player.permissions) }
    if (gameMode == null) {
        MenuItem("Debug (only available in game)", enabled = false) {}
        return
    }
    Submenu("Gamemode", enabled = mayChangeGameMode == true) {
        GameMode.entries.forEach { mode ->
            MenuItem(mode.name.lowercase().replaceFirstChar { it.uppercase() }, checked = gameMode == mode) {
                runSafe { connection.sendPacket(ChangeGameModeC2SPacket(mode)) }
            }
        }
    }
    Submenu("Debug") {
        val options = mc.options
        MenuItem("Show Advanced Tooltips", hint = "F3+H", checked = observe { options.advancedItemTooltips }.value) {
            options.advancedItemTooltips = !options.advancedItemTooltips
            options.write()
        }
        MenuItem("Pause On Lost Focus", hint = "F3+Esc", checked = observe { options.pauseOnLostFocus }.value) {
            options.pauseOnLostFocus = !options.pauseOnLostFocus
            options.write()
        }
        MenuItem("Show Debug Menu", hint = "F3", checked = observe { mc.debugHudEntryList.isF3Enabled }.value) {
            mc.debugHudEntryList.toggleF3Enabled()
        }
        MenuItem("Rendering Chart", hint = "F3+1", checked = observe { mc.debugHud.renderingChartVisible }.value) {
            mc.debugHud.toggleRenderingChart()
        }
        MenuItem("Rendering & Tick Charts", hint = "F3+2", checked = observe { mc.debugHud.renderingAndTickChartsVisible }.value) {
            mc.debugHud.toggleRenderingAndTickCharts()
        }
        MenuItem("Packet Size & Ping Charts", hint = "F3+3", checked = observe { mc.debugHud.packetSizeAndPingChartsVisible }.value) {
            mc.debugHud.togglePacketSizeAndPingCharts()
        }
        MenuDivider()
        MenuItem("Copy Location (as command)", hint = "F3+C") {
            runSafe {
                mc.keyboard.clipboard = String.format(
                    Locale.ROOT,
                    "/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
                    world.registryKey.value, player.x, player.y, player.z, player.yaw, player.pitch
                )
                GuiConfig.info("Copied location command to clipboard.")
            }
        }
        MenuItem("Clear Chat", hint = "F3+D") { mc.inGameHud?.chatHud?.clear(false) }
        MenuItem("Open Debug Options") { mc.setScreen(DebugOptionsScreen()) }
        MenuDivider()
        MenuItem("Reload Resource Packs", hint = "F3+T") {
            GuiConfig.info("Reloading resource packs...")
            mc.reloadResources()
        }
        MenuItem("Reload Chunks", hint = "F3+A") { mc.worldRenderer.reload() }
        MenuItem("Start/Stop Profiler", hint = "F3+L") { mc.toggleDebugProfiler { GuiConfig.info(it) } }
        MenuItem("Dump Dynamic Textures", hint = "F3+S") {
            val output = TextureUtil.getDebugTexturePath(gameDirectory)
            mc.textureManager.dumpDynamicTextures(output)
            GuiConfig.info("Dumped dynamic textures to: ${gameDirectory.relativize(output)}")
        }
    }
}

@Composable
private fun HelpMenu() {
    MenuItem("Documentation") { Util.getOperatingSystem().open("$REPO_URL/wiki") }
    MenuItem("GitHub Repository") { Util.getOperatingSystem().open(REPO_URL) }
    MenuItem("Report Issue") {
        mc.keyboard.clipboard = gatherDiagnostics()
        GuiConfig.info("Copied diagnostics to clipboard. Please paste it in a new issue on GitHub and click \"Submit new issue\". Thank you!")
        Util.getOperatingSystem().open("$REPO_URL/issues")
    }
    MenuItem("Check for Updates") { Util.getOperatingSystem().open("$REPO_URL/releases") }
}

@Composable
private fun FolderItem(label: String, folder: Path) = MenuItem("Open $label") { Util.getOperatingSystem().open(folder) }
