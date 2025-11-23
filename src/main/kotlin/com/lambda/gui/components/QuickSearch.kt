/*
 * Copyright 2025 Lambda
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
import com.lambda.config.AbstractSetting
import com.lambda.config.Configurable
import com.lambda.config.Configuration
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.LambdaScreen
import com.lambda.gui.Layout
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.util.KeyCode
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.StringUtils.levenshteinDistance
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.minecraft.client.gui.screen.ChatScreen
import org.lwjgl.glfw.GLFW
import kotlin.math.max

// ToDo: Add support for searching of menu bar entries
object QuickSearch {
    private val searchInput = ImString(256)
    var isOpen = false
        private set
    private var shouldFocus = false

    private var lastShiftPressTime = 0L
    private var lastShiftKeyCode = -1

    private const val DOUBLE_SHIFT_WINDOW_MS = 500L
    private const val MAX_RESULTS = 50
    private const val WINDOW_FLAGS = ImGuiWindowFlags.AlwaysAutoResize or
            ImGuiWindowFlags.NoTitleBar or
            ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoScrollbar or
            ImGuiWindowFlags.NoScrollWithMouse

    init {
        listenUnsafe<KeyboardEvent.Press> { event ->
            if (mc.currentScreen !is LambdaScreen) return@listenUnsafe
            handleKeyPress(event)
        }
    }

    interface SearchResult : Layout {
        val breadcrumb: String
    }

    private class ModuleResult(val module: Module) : SearchResult {
        override val breadcrumb = if (module is HudModule) "HUD" else "Module"

        override fun ImGuiBuilder.buildLayout() {
            with(ModuleEntry(module)) {
                buildLayout {
                    withItemWidth(ImGui.getContentRegionAvailX()) {
                        buildLayout()
                    }
                }
            }
        }
    }

    private class CommandResult(val command: LambdaCommand) : SearchResult {
        override val breadcrumb = "Command"
        override fun ImGuiBuilder.buildLayout() {
            text(command.name.capitalize())
            sameLine()
            smallButton("Insert") { mc.setScreen(ChatScreen("${CommandRegistry.prefix}${command.name} ")) }
            if (command.description.isNotBlank()) {
                sameLine()
                textDisabled(command.description)
            }
        }
    }

    private class SettingResult(val setting: AbstractSetting<*>, val configurable: Configurable) : SearchResult {
        override val breadcrumb: String by lazy { buildSettingBreadcrumb(configurable.name, setting) }

        override fun ImGuiBuilder.buildLayout() {
            with(setting) {
                buildLayout {
                    withItemWidth(ImGui.getContentRegionAvailX()) {
                        buildLayout()
                    }
                }
            }
        }
    }

    fun open() {
        isOpen = true
        shouldFocus = true
        searchInput.clear()
    }

    fun close() {
        isOpen = false
        shouldFocus = false
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun ImGuiBuilder.renderQuickSearch() {
        if (!isOpen) return
        ImGui.openPopup("QuickSearch")

        ImGui.setNextFrameWantCaptureKeyboard(true)

        val maxW = io.displaySize.x * 0.5f
        val maxH = io.displaySize.y * 0.5f

        val popupX = (io.displaySize.x - maxW) * 0.5f
        val popupY = io.displaySize.y * 0.3f
        ImGui.setNextWindowPos(popupX, popupY)
        ImGui.setNextWindowSize(maxW, 0f)
        ImGui.setNextWindowSizeConstraints(0f, 0f, maxW, maxH)

        popupModal("QuickSearch", WINDOW_FLAGS) {
            if (shouldFocus) {
                ImGui.setKeyboardFocusHere()
                shouldFocus = false
            }

            withItemWidth(ImGui.getContentRegionAvailX()) {
                withStyleVar(ImGuiStyleVar.FramePadding, style.framePadding.x, style.framePadding.y) {
                    ImGui.inputTextWithHint(
                        "##qs-input",
                        "Type to search modules, settings, and commands...",
                        searchInput,
                        ImGuiInputTextFlags.AutoSelectAll
                    )
                }
            }

            val query = searchInput.get().trim()
            if (query.isEmpty()) return@popupModal

            val results = SearchService.performSearch(query)
            if (results.isEmpty()) {
                textDisabled("Nothing found.")
                return@popupModal
            }

            val rowH = frameHeightWithSpacing
            val topArea = cursorPosY + style.windowPadding.y
            val listH = (results.size * rowH).coerceAtMost(maxH - topArea).coerceAtLeast(rowH)

            child("qs_rows", 0f, listH, false) {
                results.forEachIndexed { idx, result ->
                    withId(idx) {
                        with(result) {
                            if (breadcrumb.isNotBlank()) {
                                textDisabled(breadcrumb)
                                sameLine()
                            }
                            buildLayout()
                        }
                    }
                }
            }
        }
    }

    private object SearchService {
        private data class RankedSearchResult(val result: SearchResult, val score: Int)

        private const val MODULE_PRIORITY_BONUS = 300
        private const val HUD_MODULE_PRIORITY_BONUS = 270
        private const val COMMAND_PRIORITY_BONUS = 200

        /**
         * Calculates a relevance score for a query against a target string.
         * Returns 0 for no match. Higher scores are better.
         * The `lenient` flag adjusts the threshold for fuzzy matching.
         */
        private fun calculateScore(query: String, target: String, lenient: Boolean = false): Int {
            if (query.isEmpty() || target.isEmpty()) return 0

            // 1. Strong Matches (Exact, Prefix, Substring)
            if (target == query) return 200
            if (target.startsWith(query)) {
                val completeness = (query.length * 50) / target.length
                return 100 + completeness // Score: 101 - 150
            }
            if (target.contains(query)) {
                val completeness = (query.length * 40) / target.length
                return 50 + completeness // Score: 51 - 90
            }

            // 2. Weak Match (Fuzzy)
            val distance = query.levenshteinDistance(target)
            val strictThreshold = (query.length / 3).coerceAtLeast(1).coerceAtMost(4)
            val lenientThreshold = (query.length / 2).coerceAtLeast(2).coerceAtMost(6)
            val threshold = if (lenient) lenientThreshold else strictThreshold

            return if (distance <= threshold) {
                (50 - (distance * 10)).coerceAtLeast(1) // Score: 1-40
            } else {
                0
            }
        }

        /**
         * Performs a search and returns a list of ranked results. This is the internal
         * implementation that can be run in strict or lenient mode.
         */
        private fun searchInternal(query: String, lenient: Boolean): List<RankedSearchResult> {
            val lowerCaseQuery = query.lowercase()

            val moduleResults = ModuleRegistry.modules.mapNotNull { module ->
                val nameScore = calculateScore(lowerCaseQuery, module.name.lowercase(), lenient)
                val tagScore = calculateScore(lowerCaseQuery, module.tag.name.lowercase(), lenient)
                val bestScore = max(nameScore, tagScore)

                if (bestScore > 0) {
                    when(module) {
                        is HudModule -> RankedSearchResult(ModuleResult(module), bestScore + HUD_MODULE_PRIORITY_BONUS)
                        else -> RankedSearchResult(ModuleResult(module), bestScore + MODULE_PRIORITY_BONUS)
                    }
                } else null
            }

            val commandResults = CommandRegistry.commands.mapNotNull { command ->
                val nameScore = calculateScore(lowerCaseQuery, command.name.lowercase(), lenient)
                val aliasScore = command.aliases.maxOfOrNull { calculateScore(lowerCaseQuery, it.lowercase(), lenient) } ?: 0
                val bestScore = max(nameScore, aliasScore)

                if (bestScore > 0) {
                    RankedSearchResult(CommandResult(command), bestScore + COMMAND_PRIORITY_BONUS)
                } else null
            }

            val settingResults = Configuration.configurations.flatMap {
                it.configurables.flatMap { configurable ->
                    configurable.settings
                        .filter { setting -> setting.visibility() }
                        .mapNotNull { setting ->
                            val score = calculateScore(lowerCaseQuery, setting.name.lowercase(), lenient)
                            if (score > 0) RankedSearchResult(SettingResult(setting, configurable), score) else null
                        }
                }
            }

            return moduleResults + commandResults + settingResults
        }

        /**
         * Main search entry point. It first attempts a strict search. If no results
         * are found, it falls back to a more lenient fuzzy search.
         */
        fun performSearch(query: String): List<SearchResult> {
            // First pass: strict search for high-quality matches.
            val strictResults = searchInternal(query, lenient = false)
            if (strictResults.isNotEmpty()) {
                return strictResults
                    .sortedByDescending { it.score }
                    .map { it.result }
                    .take(MAX_RESULTS)
            }

            // Second pass: if nothing was found, perform a more generous fuzzy search.
            return searchInternal(query, lenient = true)
                .sortedByDescending { it.score }
                .map { it.result }
                .take(MAX_RESULTS)
        }
    }

    private fun buildSettingBreadcrumb(configurableName: String, setting: AbstractSetting<*>): String {
        val group = setting.groups
            .minByOrNull { it.size }
            ?.joinToString(" » ") { it.displayName }
            ?: return configurableName
        return "$configurableName » $group"
    }

    private fun handleKeyPress(event: KeyboardEvent.Press) {
        if ((!event.isPressed || event.action == GLFW.GLFW_REPEAT) ||
            !(event.keyCode == KeyCode.LeftShift.code || event.keyCode == KeyCode.RightShift.code)) return

        val currentTime = System.currentTimeMillis()
        if (lastShiftKeyCode == event.keyCode &&
            currentTime - lastShiftPressTime <= DOUBLE_SHIFT_WINDOW_MS
        ) {
            open()
            lastShiftPressTime = 0L
            lastShiftKeyCode = -1
        } else {
            lastShiftPressTime = currentTime
            lastShiftKeyCode = event.keyCode
        }
    }
}