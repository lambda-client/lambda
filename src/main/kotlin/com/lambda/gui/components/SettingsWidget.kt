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
import com.lambda.config.Setting
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.IMutableAutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiTabBarFlags
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater

object SettingsWidget {
    /**
     * Builds the settings context popup content for the given config.
     */
    fun ImGuiBuilder.buildConfigSettingsContext(config: Config) {
        group {
            if (config is Module && config != AutoUpdater) {
				button("Module Settings") {
					ImGui.openPopup("##module-settings-popup-${config.name}")
				}
	            ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
	            popupContextItem("##module-settings-popup-${config.name}", ImGuiPopupFlags.None) {
		            with(config.keybindSetting) { buildLayout() }
		            with(config.prioritySetting) { buildLayout() }
		            with(config.disableOnReleaseSetting) { buildLayout() }
		            with(config.drawSetting) { buildLayout() }
		            if (config is HudModule) {
			            with(config.backgroundColor) { buildLayout() }
		            }
		            smallButton("Reset") {
			            resetContainers(config.settingContainers)
		            }
	            }
            }
            lambdaTooltip("Resets all settings for this module to their default values")
            if (config is IMutableAutomationConfig && config.automationConfig !== AutomationConfig.DEFAULT) {
                button("Automation Config") {
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

	    if (!hasVisibleSettings(config.settingContainers)) return
	    separator()
	    renderContainers(config.settingContainers, config.name)
    }

    /**
     * Recursively renders [Config.SettingContainer]s in order.
     * - [Config.SettingContainer.Single]: renders the setting with visibility/disabled checks.
     * - [Config.SettingContainer.Tab]: renders as ImGui tab bar with tab items.
     * - [Config.SettingContainer.Group]: renders as a collapsible tree node with indent.
     */
    private fun ImGuiBuilder.renderContainers(containers: List<Config.SettingContainer>, idPrefix: String) {
	    val runs = mutableListOf<Any>()
	    containers.forEach { container ->
		    if (container is Config.SettingContainer.Tab) {
			    val last = runs.lastOrNull()
			    if (last is MutableList<*>) {
				    @Suppress("UNCHECKED_CAST")
				    (last as MutableList<Config.SettingContainer.Tab>).add(container)
			    } else {
				    runs.add(mutableListOf(container))
			    }
		    } else {
			    runs.add(container)
		    }
	    }

	    runs.forEach { run ->
		    when (run) {
			    is Config.SettingContainer.Single -> renderSetting(run.setting)
			    is Config.SettingContainer.Group -> {
				    if (hasVisibleSettings(run.settings)) {
					    treeNode("${run.name}##$idPrefix-group-${run.name}") {
						    renderContainers(run.settings, "$idPrefix-${run.name}")
					    }
				    }
			    }
			    is List<*> -> {
				    @Suppress("UNCHECKED_CAST")
				    renderTabBar(run as List<Config.SettingContainer.Tab>, idPrefix)
			    }
		    }
	    }
    }

    /**
     * Renders a group of [Config.SettingContainer.Tab]s as a single ImGui tab bar.
     */
    private fun ImGuiBuilder.renderTabBar(tabs: List<Config.SettingContainer.Tab>, idPrefix: String) {
	    val visibleTabs = tabs.filter { hasVisibleSettings(it.settings) }
	    if (visibleTabs.isEmpty()) return
	    tabBar("##$idPrefix-tabs", ImGuiTabBarFlags.FittingPolicyResizeDown) {
		    visibleTabs.forEach { tab ->
			    tabItem(tab.name) {
				    renderContainers(tab.settings, "$idPrefix-${tab.name}")
			    }
		    }
	    }
    }

    /**
     * Renders a single [Setting] with visibility and disabled state checks.
     */
    private fun ImGuiBuilder.renderSetting(setting: Setting<*, *>) {
	    if (!setting.visibility()) return
	    if (setting.disabled()) ImGui.beginDisabled()
	    with(setting) { buildLayout() }
	    if (setting.disabled()) ImGui.endDisabled()
    }

    /**
     * Checks if any [Config.SettingContainer] in the tree has a visible setting.
     */
    private fun hasVisibleSettings(containers: List<Config.SettingContainer>): Boolean =
	    containers.any { container ->
		    when (container) {
			    is Config.SettingContainer.Single -> container.setting.visibility()
			    is Config.SettingContainer.Multiple -> hasVisibleSettings(container.settings)
		    }
	    }

    /**
     * Recursively resets all settings in the container tree.
     */
    private fun resetContainers(containers: List<Config.SettingContainer>) {
	    containers.forEach { container ->
		    when (container) {
			    is Config.SettingContainer.Single -> container.setting.reset(silent = true)
			    is Config.SettingContainer.Multiple -> resetContainers(container.settings)
		    }
	    }
    }
}