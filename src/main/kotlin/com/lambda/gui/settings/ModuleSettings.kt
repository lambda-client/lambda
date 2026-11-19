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

package com.lambda.gui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.config.entries.Setting
import com.lambda.config.hasVisibleSettings
import com.lambda.config.holds
import com.lambda.gui.Reveal
import com.lambda.gui.component.Button
import com.lambda.gui.component.Dropdown
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.SoftTextShadow
import com.lambda.gui.component.Text
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater
import kotlinx.coroutines.launch

internal enum class PanelPage(val title: String) {
    Settings("Settings"),
    Module("Module"),
    Automation("Automation"),
}

@Composable
internal fun ColumnScope.ModuleSettings(module: Module, selected: PanelPage, onSelect: (PanelPage) -> Unit, scroll: ScrollState?) {
    val pages = pagesOf(module)
    val page = selected.takeIf { it in pages } ?: PanelPage.Settings
    val revealed = Reveal.setting.takeIf { Reveal.module === module }
    LaunchedEffect(revealed) {
        if (revealed != null) onSelect(pageOf(module, revealed))
    }
    val scope = rememberCoroutineScope()

    SoftTextShadow {
        if (pages.size > 1) PageStrip(pages, page) {
            onSelect(it)
            if (scroll != null) scope.launch { scroll.scrollTo(0) }
        }
        val turned = @Composable {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { pageTurn(towardsEnd = targetState > initialState) },
            ) { shown ->
                Column(Modifier.fillMaxWidth()) {
                    when (shown) {
                        PanelPage.Settings -> SettingsPage(module)
                        PanelPage.Module -> ModulePage(module)
                        PanelPage.Automation -> AutomationPage(module)
                    }
                }
            }
        }
        if (scroll != null) ScrollColumn(Modifier.weight(1f, fill = false), scroll) { turned() } else turned()
    }
}

private fun pagesOf(module: Module): List<PanelPage> = when {
    module == AutoUpdater -> listOf(PanelPage.Settings)
    module.automationConfig === AutomationConfig.DEFAULT -> listOf(PanelPage.Settings, PanelPage.Module)
    else -> PanelPage.entries
}

private fun pageOf(module: Module, setting: Setting<*>): PanelPage = when {
    module.automationConfig !== AutomationConfig.DEFAULT && module.automationConfig.settingLayers.holds(setting) -> PanelPage.Automation
    setting in moduleSettings(module) -> PanelPage.Module
    else -> PanelPage.Settings
}

private fun moduleSettings(module: Module): List<Setting<*>> = listOfNotNull(
    module.keybindSetting.takeUnless { module.alwaysEnabled },
    module.prioritySetting,
    module.disableOnReleaseSetting.takeUnless { module.alwaysEnabled },
    module.drawSetting.takeUnless { module.alwaysEnabled },
)

@Composable
private fun PageStrip(pages: List<PanelPage>, selected: PanelPage, onSelect: (PanelPage) -> Unit) {
    val colors = LambdaTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        pages.forEach { page ->
            val isSelected = page == selected
            val line by animateColorAsState(if (isSelected) colors.secondary else colors.raised)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(page.title.length + 2f)
                    .height(Look.rowHeight + 1.dp)
                    .hoverHighlight(colors.raised)
                    .mouseClickable { onSelect(page) }
                    .drawBehind {
                        val thickness = 1.dp.toPx()
                        drawRect(line, Offset(0f, size.height - thickness), Size(size.width, thickness))
                    }
                    .padding(horizontal = 4.dp)
            ) {
                Text(page.title, color = if (isSelected) colors.onSurface else colors.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SettingsPage(module: Module) {
    val any by observe { module.settingLayers.hasVisibleSettings() }
    if (any) SettingsList(module.settingLayers)
    else Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text("No settings", color = LambdaTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun AutomationPage(module: Module) {
    val linked by observe { module.backingAutomationConfig }
    CompositionLocalProvider(LocalClickOnRelease provides true) { AutomationConfigSelector(module, linked) }
    key(linked) { SettingsList(module.automationConfig.settingLayers) }
}

@Composable
private fun ModulePage(module: Module) {
    moduleSettings(module).forEach { SettingRow(it) }
    Tooltip("Resets all settings for this module to their default values", Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalClickOnRelease provides true) {
            Button("Reset", Modifier.settingCell().height(Look.rowHeight)) { module.resetSettings() }
        }
    }
}

@Composable
private fun AutomationConfigSelector(module: Module, linked: AutomationConfig) {
    val options by observe {
        listOf(module.defaultAutomationConfig) + UserAutomationCategory.configs.filterIsInstance<AutomationConfig>()
    }
    Dropdown(
        label = "Linked Config",
        options = options,
        selected = linked,
        optionLabel = { it.name },
        modifier = Modifier.settingCell().padding(start = ROW_INSET),
    ) { selected ->
        (module.backingAutomationConfig as? UserAutomationConfig)?.linkedModules?.value?.remove(module.name)
        (selected as? UserAutomationConfig)?.linkedModules?.value?.add(module.name)
        module.automationConfig = selected
    }
}
