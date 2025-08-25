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
import com.lambda.gui.Layout
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.util.KeyCode
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.StringUtils.findSimilarStrings
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.minecraft.client.gui.screen.ChatScreen

object QuickSearch {
    private val searchInput = ImString(256)
    private var isOpen = false
    private var shouldFocus = false

    private var lastShiftPressTime = 0L
    private var lastShiftKeyCode = -1

    private const val DOUBLE_SHIFT_WINDOW_MS = 500L
    private const val MAX_RESULTS = 50
    private const val SIMILARITY_THRESHOLD = 3
    private const val WINDOW_FLAGS = ImGuiWindowFlags.AlwaysAutoResize or
            ImGuiWindowFlags.NoTitleBar or
            ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoScrollbar or
            ImGuiWindowFlags.NoScrollWithMouse

    init {
        listenUnsafe<KeyboardEvent.Press> { event ->
            handleKeyPress(event)
        }
    }

    interface SearchResult : Layout {
        val breadcrumb: String
    }

    private class ModuleResult(val module: Module) : SearchResult {
        override val breadcrumb = "Module"

        override fun ImGuiBuilder.buildLayout() {
            with(ModuleEntry(module)) { buildLayout() }
        }

        companion object {
            fun search(query: String): List<ModuleResult> {
                val modules = ModuleRegistry.modules
                val direct = modules.filter {
                    it.name.lowercase().let { name -> name.startsWith(query) || name.contains(query) }
                }

                if (direct.isNotEmpty()) return direct.map(::ModuleResult)

                val names = modules.map { it.name }.toSet()
                val similar = findSimilarStrings(query, names, SIMILARITY_THRESHOLD)
                return similar.mapNotNull { name -> modules.find { it.name == name } }
                    .map(::ModuleResult)
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

        companion object {
            fun search(query: String): List<CommandResult> {
                val commands = CommandRegistry.commands
                val direct = commands.filter {
                    val name = it.name.lowercase()
                    name.startsWith(query) || name.contains(query) || it.aliases.any { alias -> alias.lowercase().contains(query) }
                }

                if (direct.isNotEmpty()) return direct.map(::CommandResult)

                val names = commands.map { it.name }.toSet()
                val similar = findSimilarStrings(query, names, SIMILARITY_THRESHOLD)
                return similar.mapNotNull { name -> commands.find { it.name == name } }
                    .map(::CommandResult)
            }
        }
    }

    private class SettingResult(val setting: AbstractSetting<*>, val configurable: Configurable) : SearchResult {
        override val breadcrumb: String by lazy { buildSettingBreadcrumb(configurable.name, setting) }

        override fun ImGuiBuilder.buildLayout() {
            with(setting) { buildLayout() }
        }

        companion object {
            fun search(query: String) =
                Configuration.configurations.flatMap { config ->
                    config.configurables.flatMap { configurable ->
                        val confNameL = configurable.name.lowercase()
                        configurable.settings.filter { setting ->
                            setting.visibility() && (setting.name.lowercase().contains(query))
                        }.map { setting ->
                            SettingResult(setting, configurable)
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
            // ToDo: Fix close with background click and escape
            if (ImGui.isKeyPressed(256)) { // ESC key
                close()
                ImGui.closeCurrentPopup()
                return@popupModal
            }

//            val bgClick = (ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1)) &&
//                    !ImGui.isWindowHovered(ImGuiHoveredFlags.AnyWindow)
//            if (bgClick) {
//                close()
//                ImGui.closeCurrentPopup()
//                return@popupModal
//            }

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

            val results = performSearch(query)
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


    private fun performSearch(query: String) =
        listOf(ModuleResult::search, CommandResult::search, SettingResult::search)
            .flatMap { it(query.lowercase()) }.take(MAX_RESULTS)

    private fun buildSettingBreadcrumb(configurableName: String, setting: AbstractSetting<*>): String {
        val group = setting.groups
            .minByOrNull { it.size }
            ?.joinToString(" » ") { it.displayName }
            ?: return configurableName
        return "$configurableName » $group"
    }

    private fun handleKeyPress(event: KeyboardEvent.Press) {
        if (!event.isPressed || !(event.keyCode == KeyCode.LEFT_SHIFT.code || event.keyCode == KeyCode.RIGHT_SHIFT.code)) return

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