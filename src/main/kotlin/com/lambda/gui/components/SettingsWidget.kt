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
import com.lambda.config.Config.SettingLayer
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
			            config.reset()
		            }
	            }
            }
            lambdaTooltip("Resets all settings for this module to their default values")
            if (config is IMutableAutomationConfig && config.automationConfig !== AutomationConfig.Default) {
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

	    if (!hasVisibleSettings(config.settingLayers)) return
	    separator()
	    drawLayers(config.settingLayers, config.name)
    }

    private fun ImGuiBuilder.drawLayers(root: SettingLayer.Multiple, idPrefix: String) {
	    var tabsDrawn = false

	    root.layers.forEach { layer ->
		    when (layer) {
			    is SettingLayer.Single<*, *> -> drawSetting(layer.setting)
			    is SettingLayer.Group -> {
				    if (hasVisibleSettings(layer)) {
					    treeNode("${layer.name}##$idPrefix-group-${layer.name}") {
						    drawLayers(layer, "$idPrefix-${layer.name}")
					    }
				    }
			    }
			    is SettingLayer.Tab -> {
				    if (!tabsDrawn) {
					    tabsDrawn = true
					    val allTabs = root.layers
						    .filterIsInstance<SettingLayer.Tab>()
						    .filter { hasVisibleSettings(it) }
					    if (allTabs.isNotEmpty()) {
						    tabBar("##$idPrefix-tabs", ImGuiTabBarFlags.FittingPolicyResizeDown) {
							    allTabs.forEach { tab ->
								    tabItem(tab.name) {
									    drawLayers(tab, "$idPrefix-${tab.name}")
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

    private fun ImGuiBuilder.drawSetting(setting: Setting<*, *>) {
	    if (!setting.visibility()) return
	    if (setting.disabled()) ImGui.beginDisabled()
	    with(setting) { buildLayout() }
	    if (setting.disabled()) ImGui.endDisabled()
    }

    private fun hasVisibleSettings(layer: SettingLayer.Multiple): Boolean =
	    layer.layers.any { layer ->
		    when (layer) {
			    is SettingLayer.Single<*, *> -> layer.setting.visibility()
			    is SettingLayer.Multiple -> hasVisibleSettings(layer)
		    }
	    }
}