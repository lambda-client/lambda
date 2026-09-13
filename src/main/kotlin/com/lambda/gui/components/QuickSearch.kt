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

package com.lambda.gui.components

import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.command.LambdaCommand
import com.lambda.config.Config
import com.lambda.config.ConfigLoader
import com.lambda.config.entries.Setting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.event.events.ButtonEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.LambdaScreen
import com.lambda.gui.Layout
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiHoveredFlags
import com.lambda.imgui.flag.ImGuiInputTextFlags
import com.lambda.imgui.flag.ImGuiMouseButton
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.imgui.type.ImString
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.util.KeyCode
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.StringUtils.levenshteinDistance
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import kotlin.math.max

@Suppress("unused")
object QuickSearch {
    private val searchInput = ImString(256)
    var isOpen = false
        private set
    var lastClosedTimestamp = 0L
        private set
    private var shouldFocus = false
    private var pendingClose = false
    private var lastShiftPressTime = 0L
    private var lastShiftKeyCode = -1

    var selectedIndex = 0
    private var needScrollToSelected = false
    private var lastSearchedQuery = ""

    enum class SearchFilter(val label: String) {
        ALL("All"),
        MODULES("Modules"),
        SETTINGS("Settings"),
        COMMANDS("Commands")
    }

    var currentFilter = SearchFilter.ALL
    private var currentResults: List<SearchResult> = emptyList()

    private const val DOUBLE_SHIFT_WINDOW_MS = 500L
    private const val MAX_RESULTS = 50
    private const val POPUP_ID = "QuickSearch"
    const val WINDOW_FLAGS =
        ImGuiWindowFlags.AlwaysAutoResize or
                ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoScrollbar or
                ImGuiWindowFlags.NoScrollWithMouse

    init {
        listenUnsafe<ButtonEvent.Keyboard.Press> { event ->
            if (mc.currentScreen !is LambdaScreen) return@listenUnsafe
            handleKeyPress(event)
        }
    }

    interface SearchResult : Layout {
        val title: String
        val breadcrumb: String
        val category: String
        val description: String
        fun onActivate()
    }

    private class ModuleResult(val module: Module) : SearchResult {
        override val title: String = module.name
        override val breadcrumb: String = if (module is HudModule) "HUD" else module.tag.name
        override val category: String = if (module is HudModule) "HUD" else module.tag.name
        override val description: String = module.description

        override fun onActivate() {
            module.toggle()
        }

        override fun ImGuiBuilder.buildLayout() {
            val isEnabled = module.isEnabled
            val primary = ClickGuiLayout.primaryColor

            // Category tag
            ImGui.textColored(primary.red / 255f, primary.green / 255f, primary.blue / 255f, 1f, "[${category.uppercase()}]")
            sameLine()

            // Module name
            text(module.name)
            sameLine()

            // Status badge
            if (isEnabled) {
                ImGui.textColored(0.2f, 0.9f, 0.3f, 1.0f, "[ON]")
            } else {
                ImGui.textColored(0.55f, 0.55f, 0.55f, 1.0f, "[OFF]")
            }

            // Keybind badge
            val bind = module.keybind
            if (bind.isKeyBind || bind.isMouseBind) {
                sameLine()
                ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "[${bind.name}]")
            }

            // Action button
            val btnLabel = if (isEnabled) "Disable##m-${module.name}" else "Enable##m-${module.name}"
            val btnW = ImGui.calcTextSize(if (isEnabled) "Disable" else "Enable").x + style.framePadding.x * 2f
            sameLine(windowContentRegionMaxX - btnW - style.windowPadding.x)
            smallButton(btnLabel) {
                module.toggle()
            }

            if (module.description.isNotBlank()) {
                textDisabled("  ${module.description}")
            }
        }
    }

    private class CommandResult(val command: LambdaCommand) : SearchResult {
        override val title: String = command.name.capitalize()
        override val breadcrumb: String = "Command"
        override val category: String = "Command"
        override val description: String = command.description

        override fun onActivate() {
            close()
            mc.setScreen(ChatScreen("${CommandRegistry.prefix}${command.name} ", true))
        }

        override fun ImGuiBuilder.buildLayout() {
            ImGui.textColored(1.0f, 0.6f, 0.2f, 1.0f, "[COMMAND]")
            sameLine()
            text("${CommandRegistry.prefix}${command.name}")

            val btnLabel = "Insert##cmd-${command.name}"
            val btnW = ImGui.calcTextSize("Insert").x + style.framePadding.x * 2f
            sameLine(windowContentRegionMaxX - btnW - style.windowPadding.x)
            smallButton(btnLabel) {
                onActivate()
            }

            if (command.description.isNotBlank()) {
                textDisabled("  ${command.description}")
            }
        }
    }

    private class SettingResult(val setting: Setting<*>, val config: Config) : SearchResult {
        override val title: String = setting.name
        override val breadcrumb: String by lazy { buildSettingBreadcrumb(config.name, setting) }
        override val category: String = "Setting"
        override val description: String = setting.description

        override fun onActivate() {
            if (setting is BooleanSetting) {
                setting.value = !setting.value
            }
        }

        override fun ImGuiBuilder.buildLayout() {
            ImGui.textColored(0.4f, 0.7f, 1.0f, 1.0f, "[SETTING]")
            sameLine()
            text(setting.name)
            sameLine()
            textDisabled("($breadcrumb)")

            // Quick toggle if boolean
            if (setting is BooleanSetting) {
                val stateText = if (setting.value) "Disable" else "Enable"
                val btnW = ImGui.calcTextSize(stateText).x + style.framePadding.x * 2f
                sameLine(windowContentRegionMaxX - btnW - style.windowPadding.x)
                smallButton("$stateText##st-${setting.name}") {
                    setting.value = !setting.value
                }
            } else {
                val valStr = setting.value.toString()
                val valPreview = if (valStr.length > 20) valStr.take(18) + "…" else valStr
                val previewW = ImGui.calcTextSize(valPreview).x + style.framePadding.x * 2f
                sameLine(windowContentRegionMaxX - previewW - style.windowPadding.x)
                textDisabled(valPreview)
            }

            if (setting.description.isNotBlank()) {
                textDisabled("  ${setting.description}")
            }
        }
    }

    fun open() {
        isOpen = true
        shouldFocus = true
        pendingClose = false
        selectedIndex = 0
        searchInput.clear()
    }

    fun close() {
        isOpen = false
        shouldFocus = false
        pendingClose = true
        lastClosedTimestamp = System.currentTimeMillis()
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun moveSelection(delta: Int) {
        if (currentResults.isEmpty()) return
        selectedIndex = (selectedIndex + delta).let {
            if (it < 0) currentResults.lastIndex
            else if (it >= currentResults.size) 0
            else it
        }
        needScrollToSelected = true
    }

    fun activateSelected() {
        currentResults.getOrNull(selectedIndex)?.onActivate()
    }

    fun ImGuiBuilder.renderQuickSearch() {
        if (!isOpen && !pendingClose) return
        if (isOpen) openPopup(POPUP_ID)

        ImGui.setNextFrameWantCaptureKeyboard(true)

        val maxW = (io.displaySize.x * 0.55f).coerceAtLeast(420f)
        val maxH = (io.displaySize.y * 0.55f).coerceAtLeast(300f)

        val popupX = (io.displaySize.x - maxW) * 0.5f
        val popupY = (io.displaySize.y - maxH) * 0.28f
        ImGui.setNextWindowPos(popupX, popupY)
        ImGui.setNextWindowSize(maxW, 0f)
        ImGui.setNextWindowSizeConstraints(maxW, 0f, maxW, maxH)

        popupModal(POPUP_ID, WINDOW_FLAGS) {
            if (pendingClose) {
                pendingClose = false
                closeCurrentPopup()
                return@popupModal
            }

            if (isMouseClicked(ImGuiMouseButton.Left) && !isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows)) {
                close()
                return@popupModal
            }

            if (shouldFocus) {
                ImGui.setKeyboardFocusHere()
                shouldFocus = false
            }

            // Search Header & Input Box
            withItemWidth(ImGui.getContentRegionAvailX()) {
                withStyleVar(ImGuiStyleVar.FramePadding, style.framePadding.x * 1.5f, style.framePadding.y * 1.5f) {
                    ImGui.inputTextWithHint(
                        "##qs-input",
                        "Search modules, settings, commands... (Ctrl+F, Esc to close)",
                        searchInput,
                        ImGuiInputTextFlags.AutoSelectAll
                    )
                }
            }

            val rawInput = searchInput.get().trim()

            // Detect prefix filters (m: modules, s: settings, c: commands)
            val (effectiveFilter, query) = when {
                rawInput.startsWith("m:", ignoreCase = true) || rawInput.startsWith("mod:", ignoreCase = true) -> {
                    SearchFilter.MODULES to rawInput.substringAfter(':').trim()
                }
                rawInput.startsWith("s:", ignoreCase = true) || rawInput.startsWith("set:", ignoreCase = true) -> {
                    SearchFilter.SETTINGS to rawInput.substringAfter(':').trim()
                }
                rawInput.startsWith("c:", ignoreCase = true) || rawInput.startsWith("cmd:", ignoreCase = true) -> {
                    SearchFilter.COMMANDS to rawInput.substringAfter(':').trim()
                }
                else -> currentFilter to rawInput
            }

            // Category Filter Buttons
            cursorPosY += 2f
            SearchFilter.entries.forEach { filter ->
                val isSelected = effectiveFilter == filter
                val label = filter.label

                if (isSelected) {
                    val primary = ClickGuiLayout.primaryColor
                    withStyleColor(com.lambda.imgui.flag.ImGuiCol.Button, primary.red / 255f, primary.green / 255f, primary.blue / 255f, 0.8f) {
                        smallButton("$label##filter") {
                            currentFilter = filter
                            selectedIndex = 0
                        }
                    }
                } else {
                    smallButton("$label##filter") {
                        currentFilter = filter
                        selectedIndex = 0
                    }
                }
                sameLine(0f, 6f)
            }
            newLine()
            separator()

            val results = if (query.isEmpty()) {
                SearchService.getQuickAccessList(effectiveFilter)
            } else {
                SearchService.performSearch(query, effectiveFilter)
            }

            if (query != lastSearchedQuery) {
                lastSearchedQuery = query
                selectedIndex = 0
            }

            currentResults = results

            if (results.isEmpty()) {
                if (query.isNotEmpty()) {
                    textDisabled("No results found for \"$query\".")
                } else {
                    textDisabled("Type to search or select a filter tab above.")
                }
                renderFooterTips()
                return@popupModal
            }

            val rowH = frameHeightWithSpacing * 1.9f
            val topArea = cursorPosY + style.windowPadding.y
            val listH = (results.size * rowH).coerceAtMost(maxH - topArea - 30f).coerceAtLeast(rowH)

            child("qs_rows", 0f, listH, false) {
                results.forEachIndexed { idx, result ->
                    withId(idx) {
                        val isSelected = idx == selectedIndex
                        val startY = cursorPosY

                        if (isSelected && needScrollToSelected) {
                            ImGui.setScrollHereY(0.5f)
                            needScrollToSelected = false
                        }

                        // Background highlight for selected row
                        val highlightAlpha = if (isSelected) 85 else 0
                        if (highlightAlpha > 0) {
                            val minX = ImGui.getWindowPosX() + style.windowPadding.x
                            val maxX = ImGui.getWindowPosX() + ImGui.getWindowWidth() - style.windowPadding.x
                            val minY = ImGui.getWindowPosY() + startY - ImGui.getScrollY()
                            val maxY = minY + rowH
                            val col = ClickGuiLayout.headerHovered
                            val packedCol = ImColor.rgba(col.red, col.green, col.blue, highlightAlpha)
                            windowDrawList.addRectFilled(minX, minY, maxX, maxY, packedCol, style.frameRounding)
                        }

                        // Interactive selectable for row selection and double-click activation
                        selectable("##row-sel-$idx", isSelected) {
                            selectedIndex = idx
                            result.onActivate()
                        }
                        if (ImGui.isItemHovered()) {
                            selectedIndex = idx
                        }

                        // Overlay content
                        cursorPosY = startY + 2f
                        with(result) {
                            buildLayout()
                        }
                        cursorPosY = startY + rowH
                    }
                }
            }

            renderFooterTips()
        }
    }

    private fun ImGuiBuilder.renderFooterTips() {
        separator()
        textDisabled("Navigate: [↑/↓]  •  Execute: [Enter]  •  Close: [Esc]  •  Prefixes: m: s: c:")
    }

    private object SearchService {
        private data class RankedSearchResult(val result: SearchResult, val score: Int)

        private const val MODULE_PRIORITY_BONUS = 300
        private const val HUD_MODULE_PRIORITY_BONUS = 270
        private const val COMMAND_PRIORITY_BONUS = 200

        fun getQuickAccessList(filter: SearchFilter): List<SearchResult> {
            val list = mutableListOf<SearchResult>()
            if (filter == SearchFilter.ALL || filter == SearchFilter.MODULES) {
                // Show enabled modules first, then prominent default modules
                val enabled = ModuleRegistry.modules.filter { it.isEnabled }.map { ModuleResult(it) }
                list.addAll(enabled)
                if (list.size < 8) {
                    val defaults = ModuleRegistry.modules.filter { !it.isEnabled }.take(10 - list.size).map { ModuleResult(it) }
                    list.addAll(defaults)
                }
            }
            if (filter == SearchFilter.COMMANDS) {
                list.addAll(CommandRegistry.commands.take(10).map { CommandResult(it) })
            }
            return list.take(MAX_RESULTS)
        }

        private fun calculateScore(query: String, target: String, lenient: Boolean = false): Int {
            if (query.isEmpty() || target.isEmpty()) return 0

            if (target == query) return 200
            if (target.startsWith(query)) {
                val completeness = (query.length * 50) / target.length
                return 100 + completeness
            }
            if (target.contains(query)) {
                val completeness = (query.length * 40) / target.length
                return 50 + completeness
            }

            val distance = query.levenshteinDistance(target)
            val strictThreshold = (query.length / 3).coerceAtLeast(1).coerceAtMost(4)
            val lenientThreshold = (query.length / 2).coerceAtLeast(2).coerceAtMost(6)
            val threshold = if (lenient) lenientThreshold else strictThreshold

            return if (distance <= threshold) {
                (50 - (distance * 10)).coerceAtLeast(1)
            } else {
                0
            }
        }

        private fun searchInternal(query: String, filter: SearchFilter, lenient: Boolean): List<RankedSearchResult> {
            val lowerCaseQuery = query.lowercase()

            val moduleResults = if (filter == SearchFilter.ALL || filter == SearchFilter.MODULES) {
                ModuleRegistry.modules.mapNotNull { module ->
                    val nameScore = calculateScore(lowerCaseQuery, module.name.lowercase(), lenient)
                    val tagScore = calculateScore(lowerCaseQuery, module.tag.name.lowercase(), lenient)
                    val bestScore = max(nameScore, tagScore)

                    if (bestScore > 0) {
                        when (module) {
                            is HudModule -> RankedSearchResult(ModuleResult(module), bestScore + HUD_MODULE_PRIORITY_BONUS)
                            else -> RankedSearchResult(ModuleResult(module), bestScore + MODULE_PRIORITY_BONUS)
                        }
                    } else null
                }
            } else emptyList()

            val commandResults = if (filter == SearchFilter.ALL || filter == SearchFilter.COMMANDS) {
                CommandRegistry.commands.mapNotNull { command ->
                    val nameScore = calculateScore(lowerCaseQuery, command.name.lowercase(), lenient)
                    val aliasScore = command.aliases.maxOfOrNull { calculateScore(lowerCaseQuery, it.lowercase(), lenient) } ?: 0
                    val bestScore = max(nameScore, aliasScore)

                    if (bestScore > 0) {
                        RankedSearchResult(CommandResult(command), bestScore + COMMAND_PRIORITY_BONUS)
                    } else null
                }
            } else emptyList()

            val settingResults = if (filter == SearchFilter.ALL || filter == SearchFilter.SETTINGS) {
                buildList {
                    ConfigLoader.configCategories.forEach { category ->
                        category.configs.forEach { config ->
                            config.settingLayers.forEachEntry { _, single ->
                                val setting = single.entry
                                if (setting.visibility()) {
                                    val score = calculateScore(lowerCaseQuery, setting.name.lowercase(), lenient)
                                    if (score > 0) add(RankedSearchResult(SettingResult(setting, config), score))
                                }
                            }
                        }
                    }
                }
            } else emptyList()

            return moduleResults + commandResults + settingResults
        }

        fun performSearch(query: String, filter: SearchFilter = SearchFilter.ALL): List<SearchResult> {
            val strictResults = searchInternal(query, filter, lenient = false)
            if (strictResults.isNotEmpty()) {
                return strictResults
                    .sortedByDescending { it.score }
                    .map { it.result }
                    .take(MAX_RESULTS)
            }

            return searchInternal(query, filter, lenient = true)
                .sortedByDescending { it.score }
                .map { it.result }
                .take(MAX_RESULTS)
        }
    }

    private fun buildSettingBreadcrumb(configName: String, setting: Setting<*>): String {
        val path = setting.getConfigCommandPath()
        return if (path.isEmpty()) configName
        else "$configName » ${path.joinToString(" » ")}"
    }

    private fun handleKeyPress(event: ButtonEvent.Keyboard.Press) {
        if (AutoUpdater.showInstallModal || AutoUpdater.showUninstallModal) return
        if (!event.isPressed || event.isRepeated) return

        // Navigation when QuickSearch is open
        if (isOpen) {
            when (event.keyCode) {
                GLFW.GLFW_KEY_DOWN -> {
                    moveSelection(1)
                    event.cancel()
                    return
                }
                GLFW.GLFW_KEY_UP -> {
                    moveSelection(-1)
                    event.cancel()
                    return
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    activateSelected()
                    event.cancel()
                    return
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    close()
                    event.cancel()
                    return
                }
                GLFW.GLFW_KEY_TAB -> {
                    // Cycle filter
                    val values = SearchFilter.entries
                    val nextIdx = (currentFilter.ordinal + 1) % values.size
                    currentFilter = values[nextIdx]
                    selectedIndex = 0
                    event.cancel()
                    return
                }
            }
        }
        // Ctrl+F shortcut to open/toggle
        val win = mc.window
        val isCtrl = (event.modifiers and GLFW.GLFW_MOD_CONTROL != 0)
            || (win != null && (InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_RIGHT_CONTROL)))
        if (event.keyCode == GLFW.GLFW_KEY_F && isCtrl) {
            toggle()
            event.cancel()
            return
        }
        if (event.keyCode == KeyCode.LeftShift.code || event.keyCode == KeyCode.RightShift.code) {
            val currentTime = System.currentTimeMillis()
            if (lastShiftKeyCode == event.keyCode &&
                currentTime - lastShiftPressTime <= DOUBLE_SHIFT_WINDOW_MS
            ) {
                toggle()
                lastShiftPressTime = 0L
                lastShiftKeyCode = -1
            } else {
                lastShiftPressTime = currentTime
                lastShiftKeyCode = event.keyCode
            }
        }
    }
}
