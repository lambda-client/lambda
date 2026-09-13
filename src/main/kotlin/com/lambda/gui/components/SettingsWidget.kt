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

import com.lambda.config.Config
import com.lambda.config.EntryLayer
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.IMutableAutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiTabBarFlags
import com.lambda.imgui.type.ImString
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater

object SettingsWidget {
    private val settingFilters = mutableMapOf<String, ImString>()

    /**
     * Builds the settings content for the given config (used in popups and inline expansion).
     */
    fun ImGuiBuilder.buildConfigSettingsContext(config: Config) {
        group {
            if (config is Module && config != AutoUpdater) {
                withId("##header-${config.name}") {
                    // Row 1: Keybind setting + Reset button
                    with(config.keybindSetting) { buildLayout() }

                    sameLine()
                    val resetText = "Reset"
                    val resetBtnW = ImGui.calcTextSize(resetText).x + style.framePadding.x * 2.5f
                    cursorPosX = (windowContentRegionMaxX - resetBtnW - style.windowPadding.x).coerceAtLeast(cursorPosX + 10f)
                    smallButton("$resetText##rst-${config.name}") {
                        config.resetSettings()
                    }
                    lambdaTooltip("Reset all settings for ${config.name} to their default values")

                    // Row 2: Draw on HUD and Toggle on Release checkboxes
                    checkbox("Draw on HUD##draw-${config.name}", config.drawSetting::value)
                    lambdaTooltip(config.drawSetting.description)

                    sameLine(0f, 15f)
                    checkbox("Toggle on Release##rel-${config.name}", config.disableOnReleaseSetting::value)
                    lambdaTooltip("Disable this module when the bound key is released")

                    if (config is HudModule) {
                        sameLine(0f, 15f)
                        with(config.backgroundColor) { buildLayout() }
                    }

                    // Collapsible Advanced section for priority
                    treeNode("Advanced Options##adv-${config.name}") {
                        with(config.prioritySetting) { buildLayout() }
                    }
                }
            }

            if (config is IMutableAutomationConfig && config.automationConfig !== AutomationConfig.DEFAULT) {
                button("Automation Config##btn-${config.name}") {
                    ImGui.openPopup("##automation-config-popup-${config.name}")
                }
                if (config.backingAutomationConfig !== config.defaultAutomationConfig) {
                    sameLine()
                    text("(${config.backingAutomationConfig.name})")
                }
                ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
                popupContextItem("##automation-config-popup-${config.name}", ImGuiPopupFlags.None) {
                    combo("##LinkedConfig", preview = "Linked Config: ${config.backingAutomationConfig.name}") {
                        val addItem: (Config) -> Unit = { item ->
                            val selected = item === config.backingAutomationConfig

                            selectable(item.name, selected) {
                                if (!selected) {
                                    (config.backingAutomationConfig as? UserAutomationConfig)?.linkedModules?.value?.remove(config.name)
                                    (item as? UserAutomationConfig)?.linkedModules?.value?.add(config.name)
                                    config.automationConfig = item as? AutomationConfig ?: return@selectable
                                }
                            }
                        }
                        addItem(config.defaultAutomationConfig)
                        UserAutomationCategory.configs.forEach { addItem(it) }
                    }
                    buildConfigSettingsContext(config.automationConfig)
                }
            }
        }

        if (!hasVisibleSettings(config.settingLayers)) return
        separator()

        // Setting search filter if module has 4 or more visible settings
        val visibleCount = countVisibleSettings(config.settingLayers)
        var filterQuery = ""
        if (visibleCount >= 4) {
            val filter = settingFilters.getOrPut(config.name) { ImString(64) }
            withItemWidth(ImGui.getContentRegionAvailX()) {
                ImGui.inputTextWithHint("##st-filter-${config.name}", "Filter ${config.name} settings...", filter)
            }
            filterQuery = filter.get().trim()
            separator()
        }

        drawLayers(config.settingLayers, config.name, filterQuery)
    }

    private fun ImGuiBuilder.drawLayers(root: EntryLayer.Multiple<Setting<*>>, idPrefix: String, filter: String) {
        var tabsDrawn = false

        root.layers.forEach { layer ->
            when (layer) {
                is EntryLayer.Single<Setting<*>> -> {
                    if (filter.isEmpty() || layer.entry.name.contains(filter, ignoreCase = true)) {
                        drawSetting(layer.entry)
                    }
                }
                is EntryLayer.Group -> {
                    if (hasVisibleSettings(layer, filter)) {
                        treeNode("${layer.name}##$idPrefix-group-${layer.name}") {
                            drawLayers(layer, "$idPrefix-${layer.name}", filter)
                        }
                    }
                }
                is EntryLayer.Tab -> {
                    if (!tabsDrawn) {
                        tabsDrawn = true
                        val allTabs = root.layers
                            .filterIsInstance<EntryLayer.Tab<Setting<*>>>()
                            .filter { hasVisibleSettings(it, filter) }
                        if (allTabs.isNotEmpty()) {
                            tabBar("##$idPrefix-tabs", ImGuiTabBarFlags.FittingPolicyResizeDown) {
                                allTabs.forEach { tab ->
                                    tabItem(tab.name) {
                                        drawLayers(tab, "$idPrefix-${tab.name}", filter)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun ImGuiBuilder.drawSetting(setting: Setting<*>) {
        if (!setting.visibility()) return
        if (setting.disabled()) ImGui.beginDisabled()
        with(setting) { buildLayout() }
        if (setting.disabled()) ImGui.endDisabled()
    }

    private fun countVisibleSettings(layer: EntryLayer.Multiple<Setting<*>>): Int {
        var count = 0
        layer.layers.forEach { l ->
            when (l) {
                is SettingEntryLayer<*, *> -> if (l.entry.visibility()) count++
                is EntryLayer.Multiple<Setting<*>> -> count += countVisibleSettings(l)
                else -> {}
            }
        }
        return count
    }

    private fun hasVisibleSettings(layer: EntryLayer.Multiple<Setting<*>>, filter: String = ""): Boolean =
        layer.layers.any { l ->
            when (l) {
                is SettingEntryLayer<*, *> -> l.entry.visibility() && (filter.isEmpty() || l.entry.name.contains(filter, ignoreCase = true))
                is EntryLayer.Multiple<Setting<*>> -> hasVisibleSettings(l, filter)
                else -> false
            }
        }
}
