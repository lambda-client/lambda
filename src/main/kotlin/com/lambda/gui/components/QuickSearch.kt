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

import com.lambda.command.CommandRegistry
import com.lambda.config.AbstractSetting
import com.lambda.config.Configuration
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.util.StringUtils.findSimilarStrings
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString

object QuickSearch {
    private val searchInput = ImString(256)
    private var isOpen = false
    private var shouldFocus = false
    private val maxResults = 50
    private val searchThreshold = 3

    data class SearchResult(
        val name: String,
        val type: SearchResultType,
        val description: String = "",
        val action: () -> Unit
    )

    enum class SearchResultType(val displayName: String) {
        MODULE("Module"),
        SETTING("Setting"),
        COMMAND("Command")
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

        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoResize

        popupModal("QuickSearch", flags) {
            // Set popup position to center of screen
            val displaySize = ImGui.getIO().displaySize
            val popupSize = ImGui.getWindowSize()
            ImGui.setWindowPos(
                (displaySize.x - popupSize.x) * 0.5f,
                (displaySize.y - popupSize.y) * 0.3f
            )

            text("Quick Search")
            separator()

            // Search input
            if (shouldFocus) {
                ImGui.setKeyboardFocusHere()
                shouldFocus = false
            }

            val searchChanged = inputText("##search", searchInput, ImGuiInputTextFlags.AutoSelectAll)

            // Handle escape key to close (simplified)
            if (ImGui.isKeyPressed(256)) { // ImGuiKey.Escape
                close()
                ImGui.closeCurrentPopup()
                return@popupModal
            }

            // Handle enter key (simplified)
            if (ImGui.isKeyPressed(257)) { // ImGuiKey.Enter  
                val results = performSearch(searchInput.get())
                if (results.isNotEmpty()) {
                    results.first().action()
                    close()
                    ImGui.closeCurrentPopup()
                    return@popupModal
                }
            }

            separator()

            // Search results
            val query = searchInput.get().trim()
            if (query.isNotEmpty()) {
                val results = performSearch(query)
                
                if (results.isEmpty()) {
                    textDisabled("No results found")
                } else {
                    text("Results (${results.size}):")
                    child("SearchResults", 400f, 300f, true) {
                        results.forEachIndexed { index, result ->
                            val isSelected = index == 0 // Highlight first result
                            selectable("${result.name}##${result.type}", isSelected) {
                                result.action()
                                close()
                                ImGui.closeCurrentPopup()
                            }
                            
                            if (ImGui.isItemHovered()) {
                                lambdaTooltip("${result.type.displayName}: ${result.name}\n${result.description}")
                            }
                            
                            sameLine()
                            textDisabled("[${result.type.displayName}]")
                        }
                    }
                }
            } else {
                textDisabled("Type to search modules, settings, and commands...")
                text("")
                text("Examples:")
                bulletText("'auto' - find AutoWalk, AutoTool, etc.")
                bulletText("'gui' - find ClickGUI, GuiSettings, etc.")
                bulletText("'speed' - find Speed module, speedSettings, etc.")
            }

            separator()

            button("Close") {
                close()
                ImGui.closeCurrentPopup()
            }
            
            sameLine()
            textDisabled("Tip: Press Shift+Shift to open quickly")
        }
    }

    private fun performSearch(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        // Search modules
        searchModules(lowerQuery, results)
        
        // Search commands
        searchCommands(lowerQuery, results)
        
        // Search settings
        searchSettings(lowerQuery, results)

        return results.sortedBy { it.name.lowercase() }.take(maxResults)
    }

    private fun searchModules(query: String, results: MutableList<SearchResult>) {
        // Direct name matches first
        ModuleRegistry.modules.forEach { module ->
            val moduleNameLower = module.name.lowercase()
            if (moduleNameLower.contains(query) || moduleNameLower.startsWith(query)) {
                results.add(SearchResult(
                    name = module.name,
                    type = SearchResultType.MODULE,
                    description = "${module.description} (${if (module.isEnabled) "Enabled" else "Disabled"})",
                    action = { module.toggle() }
                ))
            }
        }

        // Fuzzy matches for modules if not too many direct matches
        if (results.count { it.type == SearchResultType.MODULE } < 10) {
            val moduleNames = ModuleRegistry.modules.map { it.name }.toSet()
            val similarNames = findSimilarStrings(query, moduleNames, searchThreshold)
            
            similarNames.forEach { name ->
                val module = ModuleRegistry.modules.find { it.name == name }
                if (module != null && results.none { it.name == name && it.type == SearchResultType.MODULE }) {
                    results.add(SearchResult(
                        name = module.name,
                        type = SearchResultType.MODULE,
                        description = "${module.description} (${if (module.isEnabled) "Enabled" else "Disabled"})",
                        action = { module.toggle() }
                    ))
                }
            }
        }
    }

    private fun searchCommands(query: String, results: MutableList<SearchResult>) {
        CommandRegistry.commands.forEach { command ->
            val commandNameLower = command.name.lowercase()
            if (commandNameLower.contains(query) || 
                command.aliases.any { it.lowercase().contains(query) }) {
                results.add(SearchResult(
                    name = command.name,
                    type = SearchResultType.COMMAND,
                    description = "${command.description} (prefix: ${CommandRegistry.prefix})",
                    action = { 
                        // For commands, show info about usage
                        println("Command: ${CommandRegistry.prefix}${command.name} - ${command.description}")
                    }
                ))
            }
        }

        // Add fuzzy matching for commands too
        if (results.count { it.type == SearchResultType.COMMAND } < 5) {
            val commandNames = CommandRegistry.commands.map { it.name }.toSet()
            val similarNames = findSimilarStrings(query, commandNames, searchThreshold)
            
            similarNames.forEach { name ->
                val command = CommandRegistry.commands.find { it.name == name }
                if (command != null && results.none { it.name == name && it.type == SearchResultType.COMMAND }) {
                    results.add(SearchResult(
                        name = command.name,
                        type = SearchResultType.COMMAND,
                        description = "${command.description} (prefix: ${CommandRegistry.prefix})",
                        action = { 
                            println("Command: ${CommandRegistry.prefix}${command.name} - ${command.description}")
                        }
                    ))
                }
            }
        }
    }

    private fun searchSettings(query: String, results: MutableList<SearchResult>) {
        // Limit setting search to avoid too many results
        var settingCount = 0
        val maxSettings = 15
        
        Configuration.configurations.forEach { config ->
            if (settingCount >= maxSettings) return@forEach
            
            config.configurables.forEach { configurable ->
                if (settingCount >= maxSettings) return@forEach
                
                configurable.settings.forEach { setting ->
                    if (settingCount >= maxSettings) return@forEach
                    
                    val settingNameLower = setting.name.lowercase()
                    val configurableName = configurable.name.lowercase()
                    
                    if (settingNameLower.contains(query) || configurableName.contains(query)) {
                        results.add(SearchResult(
                            name = "${configurable.name}.${setting.name}",
                            type = SearchResultType.SETTING,
                            description = setting.description.ifEmpty { "Setting in ${configurable.name}" },
                            action = {
                                // For settings, show current value
                                println("Setting: ${configurable.name}.${setting.name} = ${setting.value}")
                            }
                        ))
                        settingCount++
                    }
                }
            }
        }
    }
}