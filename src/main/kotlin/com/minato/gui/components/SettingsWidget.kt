
package com.minato.gui.components

import com.minato.config.Config
import com.minato.config.EntryLayer
import com.minato.config.automation.AutomationConfig
import com.minato.config.automation.IMutableAutomationConfig
import com.minato.config.automation.UserAutomationConfig
import com.minato.config.categories.UserAutomationCategory
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiPopupFlags
import com.lambda.imgui.flag.ImGuiTabBarFlags
import com.minato.module.HudModule
import com.minato.module.Module

object SettingsWidget {
    /**
     * Builds the settings context popup content for the given config.
     */
    fun ImGuiBuilder.buildConfigSettingsContext(config: Config) {
        group {
            if (config is Module) {
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
			            config.resetSettings()
		            }
	            }
            }
            minatoTooltip("Resets all settings for this module to their default values")
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

	    if (!hasVisibleSettings(config.settingLayers)) return
	    separator()
	    drawLayers(config.settingLayers, config.name)
    }

    private fun ImGuiBuilder.drawLayers(root: EntryLayer.Multiple<Setting<*>>, idPrefix: String) {
	    var tabsDrawn = false

	    root.layers.forEach { layer ->
		    when (layer) {
			    is EntryLayer.Single<Setting<*>> -> drawSetting(layer.entry)
			    is EntryLayer.Group -> {
				    if (hasVisibleSettings(layer)) {
					    treeNode("${layer.name}##$idPrefix-group-${layer.name}") {
						    drawLayers(layer, "$idPrefix-${layer.name}")
					    }
				    }
			    }
			    is EntryLayer.Tab -> {
				    if (!tabsDrawn) {
					    tabsDrawn = true
					    val allTabs = root.layers
						    .filterIsInstance<EntryLayer.Tab<Setting<*>>>()
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

    private fun ImGuiBuilder.drawSetting(setting: Setting<*>) {
	    if (!setting.visibility()) return
	    if (setting.disabled()) ImGui.beginDisabled()
	    with(setting) { buildLayout() }
	    if (setting.disabled()) ImGui.endDisabled()
    }

    private fun hasVisibleSettings(layer: EntryLayer.Multiple<Setting<*>>): Boolean =
	    layer.layers.any { layer ->
		    when (layer) {
			    is SettingEntryLayer<*, *> -> layer.entry.visibility()
			    is EntryLayer.Multiple<Setting<*>> -> hasVisibleSettings(layer)
			    else -> false
		    }
	    }
}