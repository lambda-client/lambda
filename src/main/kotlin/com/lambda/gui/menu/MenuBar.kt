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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.lambda.config.ClientConfig
import com.lambda.config.ConfigLoader
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.config.settings.complex.Bind
import com.lambda.gui.GuiConfig
import com.lambda.gui.LambdaGui
import com.lambda.gui.clickgui.CLICK_GUI_CATEGORIES
import com.lambda.gui.clickgui.ClickGuiDialog
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.gui.component.Centered
import com.lambda.gui.component.Menu
import com.lambda.gui.component.MenuDivider
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuLabel
import com.lambda.gui.component.MenuState
import com.lambda.gui.component.Submenu
import com.lambda.gui.component.Suggestions
import com.lambda.gui.component.Text
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

internal val MENU_BAR_HEIGHT = 15.dp
private val ICON_SIZE = 9.dp
private val SEARCH_WIDTH = 150.dp
private val RESULTS_WIDTH = 260.dp
private val RESULT_GAP = 10.dp

@Composable
internal fun MenuBar(modifier: Modifier = Modifier) {
	val colors = LambdaTheme.colors
	var bar by remember { mutableStateOf(IntSize.Zero) }
	var menus by remember { mutableStateOf(IntSize.Zero) }
	var search by remember { mutableStateOf(IntSize.Zero) }
	Box(
		contentAlignment = Centered.BOTH,
		modifier = modifier
			.fillMaxWidth()
			.height(MENU_BAR_HEIGHT)
			.onSizeChanged { bar = it }
			.glass(colors.surface, RectangleShape)
			.consumeClicks()
	) {
		Row(
			Modifier
				.align(Centered.START)
				.fillMaxHeight()
				.onSizeChanged { menus = it },
			verticalAlignment = Centered.VERTICALLY
		) {
			BarMenu("Lambda", icon = "textures/lambda.png") { LambdaMenu() }
			BarMenu("Modules") { ModulesMenu() }
			BarMenu("Automation") { AutomationMenu() }
			BarMenu("Minecraft") { MinecraftMenu() }
			BarMenu("Help") { HelpMenu() }
		}
		SearchField(
			Modifier
				.onSizeChanged { search = it }
				.offset {
					IntOffset(
						(menus.width - ((bar.width - search.width) / 2)).coerceAtLeast(0),
						0
					)
				}
		)
	}
}

@Composable
private fun BarMenu(
	label: String,
	icon: String? = null,
	content: @Composable ColumnScope.() -> Unit
) {
	val menu = remember { MenuState() }
	Box(
		contentAlignment = Centered.BOTH,
		modifier = Modifier
			.fillMaxHeight()
			.hoverHighlight(LambdaTheme.colors.controlSelected)
			.mouseClickable(onClick = menu::toggle)
			.padding(horizontal = 6.dp)
	) {
		Row(
			verticalAlignment = Centered.VERTICALLY,
			horizontalArrangement = Arrangement.spacedBy(3.dp)
		) {
			if (icon != null) {
				Image(
					rememberTexture(icon),
					contentDescription = null,
					Modifier.size(ICON_SIZE),
					filterQuality = FilterQuality.High
				)
			}
			Text(label, maxLines = 1)
		}
		Menu(menu.isOpen, onDismiss = menu::dismiss, content = content)
	}
}

@Composable
private fun SearchField(modifier: Modifier = Modifier) {
	val focus = LocalFocusManager.current
	val field = remember { FocusRequester() }
	val asked = ClickGuiState.focusSearch
	LaunchedEffect(asked) {
		if (asked) field.requestFocus()
		ClickGuiState.focusSearch = false
	}
	var query by remember { mutableStateOf("") }
	var listed by remember { mutableStateOf(false) }
	val results = remember(query) { Search.find(query.trim()) }
	val open = listed && query.isNotBlank()
	val shown = rememberLast(results.takeIf { open }).orEmpty()
	var marked by remember(results) { mutableStateOf(0) }
	val list = rememberLazyListState()
	LaunchedEffect(results, marked) {
		if (open) list.reveal(marked)
	}

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
				event.key == Key.Escape && query.isNotEmpty() -> {
					query = ""
					listed = false
					true
				}
				event.key == Key.Enter -> {
					if (open) results.getOrNull(marked)?.let(::choose)
					true
				}
				!open || results.isEmpty() -> false
				event.key == Key.DirectionDown ||
						(event.key == Key.Tab && !event.isShiftPressed) -> {
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
			modifier = Modifier.width(SEARCH_WIDTH).focusRequester(field),
			placeholder = "Search modules and settings",
		)
		Suggestions(open, onDismiss = { listed = false }, RESULTS_WIDTH, list) {
			if (shown.isEmpty()) item { MenuLabel("Nothing found") }
			itemsIndexed(shown) { index, result ->
				ResultRow(
					result,
					marked = index == marked,
					onPoint = { marked = index }
				) {
					choose(result)
				}
			}
		}
	}
}

private suspend fun LazyListState.reveal(index: Int) {
	val rows = layoutInfo.visibleItemsInfo
	val height = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
	val row = rows.firstOrNull { it.index == index }
	val rowHeight = (row ?: rows.firstOrNull())?.size ?: 0
	val first = rows.firstOrNull()?.index ?: 0
	when {
		row == null && index < first -> scrollToItem(index)
		row == null -> scrollToItem(index, rowHeight - height)
		row.offset < 0 -> scrollToItem(index)
		row.offset + row.size > height -> scrollToItem(index, rowHeight - height)
	}
}

@Composable
private fun ResultRow(
	result: Search.Result,
	marked: Boolean,
	onPoint: () -> Unit,
	onClick: () -> Unit
) {
	val colors = LambdaTheme.colors
	val point by rememberUpdatedState(onPoint)
	Row(
		verticalAlignment = Centered.VERTICALLY,
		modifier = Modifier
			.fillMaxWidth()
			.background(if (marked) colors.controlSelected else Color.Transparent)
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
		Spacer(Modifier.width(RESULT_GAP))
		Box(Modifier.weight(1f), contentAlignment = Centered.END) {
			Text(
				result.origin,
				color = colors.onSurfaceVariant,
				style = Type.label,
				maxLines = 1
			)
		}
	}
}

@Composable
private fun LambdaMenu() {
	Submenu("Save Config") {
		MenuItem("Save All Configs") {
			ConfigLoader.configCategories.forEach { it.trySaveToFile(true) }
			GuiConfig.info(
				"Saved ${ConfigLoader.configCategories.size} configuration files."
			)
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
			GuiConfig.info(
				"Loaded ${ConfigLoader.configCategories.size} configuration files."
			)
		}
		ConfigLoader.configCategories.forEach { category ->
			MenuItem("Load ${category.name}") {
				category.tryLoadFromFile()
				GuiConfig.info("Loaded ${category.name}")
			}
		}
	}
	MenuDivider()
	MenuItem("Client Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(ClientConfig) }
	MenuItem("GUI Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(GuiConfig) }
	MenuItem("HUD Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(HudConfig) }
	MenuItem("Lambda API Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(LambdaAPI) }
	MenuItem("Baritone Settings...") { ClickGuiState.dialog = ClickGuiDialog.Settings(BaritoneHandler) }
	MenuDivider()
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
	MenuDivider()
	MenuItem("Developer Mode", checked = ClickGuiState.developerMode) {
		ClickGuiState.developerMode = !ClickGuiState.developerMode
	}
	MenuDivider()
	val keybind by observe { GuiConfig.keybind }
	val closeKeys =
		if (keybind == Bind.EMPTY) "Esc"
		else "Esc/${keybind.name}"
	MenuItem("Close GUI", hint = closeKeys) { LambdaGui.back() }
	MenuItem("Exit Client") { mc.scheduleStop() }
}

@Composable
private fun ModulesMenu() {
	Submenu("Shown Categories") {
		CLICK_GUI_CATEGORIES.forEach { tag ->
			val shown by observe { ModuleTag.isTagShown(tag) }
			MenuItem(tag.name, checked = shown) { ModuleTag.toggleTag(tag) }
		}
	}
	MenuDivider()
	CLICK_GUI_CATEGORIES.forEach { tag ->
		Submenu(tag.name) {
			ModuleRegistry.modules.filter { it.tag == tag }.forEach { module ->
				val shown by observe { module.showInClickGui.value }
				MenuItem(module.name, checked = shown) {
					module.showInClickGui.value = !shown
				}
			}
		}
	}
}

@Composable
private fun AutomationMenu() {
	val configs by
		observe {
			UserAutomationCategory.configs.filterIsInstance<UserAutomationConfig>()
		}
	MenuItem("New Config...") {
		ClickGuiState.dialog = ClickGuiDialog.NewAutomationConfig
	}
	MenuDivider()
	configs.forEach { config ->
		MenuItem("${config.name}...") {
			ClickGuiState.dialog = ClickGuiDialog.Settings(config)
		}
	}
	MenuItem("${AutomationConfig.DEFAULT.name}...") {
		ClickGuiState.dialog = ClickGuiDialog.Settings(AutomationConfig.DEFAULT)
	}
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
	val mayChangeGameMode by
		observeSafe { GameModeCommand.PERMISSION_CHECK.allows(player.permissions) }
	if (gameMode == null) {
		MenuItem("Debug (only available in game)", enabled = false) {}
		return
	}
	Submenu("Gamemode", enabled = mayChangeGameMode == true) {
		GameMode.entries.forEach { mode ->
			MenuItem(
				mode.name.lowercase().replaceFirstChar { it.uppercase() },
				checked = gameMode == mode
			) {
				runSafe { connection.sendPacket(ChangeGameModeC2SPacket(mode)) }
			}
		}
	}
	Submenu("Debug") {
		val options = mc.options
		MenuItem(
			"Show Advanced Tooltips",
			hint = "F3+H",
			checked = observe { options.advancedItemTooltips }.value
		) {
			options.advancedItemTooltips = !options.advancedItemTooltips
			options.write()
		}
		MenuItem(
			"Pause On Lost Focus",
			hint = "F3+Esc",
			checked = observe { options.pauseOnLostFocus }.value
		) {
			options.pauseOnLostFocus = !options.pauseOnLostFocus
			options.write()
		}
		MenuItem(
			"Show Debug Menu",
			hint = "F3",
			checked = observe { mc.debugHudEntryList.isF3Enabled }.value
		) {
			mc.debugHudEntryList.toggleF3Enabled()
		}
		MenuItem(
			"Rendering Chart",
			hint = "F3+1",
			checked = observe { mc.debugHud.renderingChartVisible }.value
		) {
			mc.debugHud.toggleRenderingChart()
		}
		MenuItem(
			"Rendering & Tick Charts",
			hint = "F3+2",
			checked = observe { mc.debugHud.renderingAndTickChartsVisible }.value
		) {
			mc.debugHud.toggleRenderingAndTickCharts()
		}
		MenuItem(
			"Packet Size & Ping Charts",
			hint = "F3+3",
			checked = observe { mc.debugHud.packetSizeAndPingChartsVisible }.value
		) {
			mc.debugHud.togglePacketSizeAndPingCharts()
		}
		MenuDivider()
		MenuItem("Copy Location (as command)", hint = "F3+C") {
			runSafe {
				mc.keyboard.clipboard =
					String.format(
						Locale.ROOT,
						"/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
						world.registryKey.value,
						player.x,
						player.y,
						player.z,
						player.yaw,
						player.pitch
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
		MenuItem("Start/Stop Profiler", hint = "F3+L") {
			mc.toggleDebugProfiler { GuiConfig.info(it) }
		}
		MenuItem("Dump Dynamic Textures", hint = "F3+S") {
			val output = TextureUtil.getDebugTexturePath(gameDirectory)
			mc.textureManager.dumpDynamicTextures(output)
			GuiConfig.info(
				"Dumped dynamic textures to: ${gameDirectory.relativize(output)}"
			)
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
private fun FolderItem(label: String, folder: Path) =
	MenuItem("Open $label") { Util.getOperatingSystem().open(folder) }
