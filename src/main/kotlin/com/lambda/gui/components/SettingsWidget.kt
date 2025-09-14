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

import com.lambda.config.AbstractSetting
import com.lambda.config.Configurable
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.Module
import com.lambda.util.NamedEnum
import imgui.flag.ImGuiTabBarFlags

object SettingsWidget {
    /**
     * Builds the settings context popup content for a given configurable.
     */
    fun ImGuiBuilder.buildConfigSettingsContext(config: Configurable) {
        group {
            if (config is Module) {
                with(config.keybindSetting) { buildLayout() }
            }
            sameLine()
            smallButton("Reset") {
                config.settings.forEach { it.reset(silent = true) }
            }
            lambdaTooltip("Resets all settings for this module to their default values")
        }
        separator()
        val toIgnoreSettings = if (config is Module) setOf(config.keybindSetting) else emptySet()
        val visibleSettings = config.settings.filter { it.visibility() } - toIgnoreSettings
        val (grouped, ungrouped) = visibleSettings.partition { it.groups.isNotEmpty() }
        ungrouped.forEach { with(it) { buildLayout() } }
        renderGroup(grouped, emptyList(), config)
    }

    private fun ImGuiBuilder.renderGroup(
        settings: List<AbstractSetting<*>>,
        parentPath: List<NamedEnum>,
        config: Configurable
    ) {
        settings.filter { it.groups.contains(parentPath) }.forEach { with(it) { buildLayout() } }

        val subGroupSettings = settings.filter { s ->
            s.groups.any { it.size > parentPath.size && it.subList(0, parentPath.size) == parentPath }
        }
        val subTabs = subGroupSettings
            .flatMap { s ->
                s.groups.mapNotNull { path ->
                    if (path.size > parentPath.size && path.subList(0, parentPath.size) == parentPath)
                        path[parentPath.size] else null
                }
            }.distinct()

        if (subTabs.isNotEmpty()) {
            val id = "##${config.name}-tabs-${parentPath.joinToString("-") { it.displayName }}"
            tabBar(id, ImGuiTabBarFlags.FittingPolicyResizeDown) {
                subTabs.forEach { tab ->
                    tabItem(tab.displayName) {
                        val newParentPath = parentPath + tab
                        val settingsForSubGroup = subGroupSettings.filter { s ->
                            s.groups.any { it.size >= newParentPath.size && it.subList(0, newParentPath.size) == newParentPath }
                        }
                        renderGroup(settingsForSubGroup, newParentPath, config)
                    }
                }
            }
        }
    }
}