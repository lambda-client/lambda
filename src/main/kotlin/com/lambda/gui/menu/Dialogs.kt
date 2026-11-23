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

package com.lambda.gui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.lambda.Lambda
import com.lambda.Lambda.REPO_URL
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.config.Config
import com.lambda.config.ConfigLoader
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.core.Loader
import com.lambda.event.EventFlow
import com.lambda.gui.component.Button
import com.lambda.gui.component.Centered
import com.lambda.gui.component.Dialog
import com.lambda.gui.component.Text
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.rememberTexture
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.observe
import com.lambda.gui.settings.ConfigSettings
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Type
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.util.Diagnostics.gatherDiagnostics
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Util

@Composable
internal fun ConfigDialog(config: Config, onDismiss: () -> Unit) {
    val maxHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        } * 0.7f
    Dialog(onDismiss) {
        ConfigSettings(config, maxHeight, onDismiss, Modifier.width(240.dp))
    }
}

@Composable
internal fun NewAutomationConfigDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val isAvailable = name.isNotBlank() && ConfigLoader.configs.none { it.name == name }

    fun create() {
        if (!isAvailable) return
        UserAutomationConfig(name)
        onDismiss()
    }

    Dialog(onDismiss) {
        Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("New Automation Config", style = Type.title)
            TextInput(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                placeholder = "Name",
                focused = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1f))
                Button("Cancel", onClick = onDismiss)
                Button("Create", enabled = isAvailable, onClick = ::create)
            }
        }
    }
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val colors = LambdaTheme.colors
    val facts by
        observe {
            val settings =
                ConfigLoader.configCategories.sumOf { category ->
                    category.configs.sumOf { config ->
                        var count = 0
                        config.settingLayers.forEachEntry { _, _ -> count++ }
                        count
                    }
                }
            listOfNotNull(
                "Version: ${Lambda.VERSION}",
                "Development Environment".takeIf { Lambda.isDebug },
                "Runtime: ${Loader.runtime}",
                "Modules: ${ModuleRegistry.modules.size}",
                "Commands: ${CommandRegistry.commands.size}",
                "Settings: $settings",
                "Synchronous listeners: ${EventFlow.syncListeners.size}",
                "Concurrent listeners: ${EventFlow.concurrentListeners.size}",
            )
        }
    val authors =
        remember {
            FabricLoader.getInstance()
                .getModContainer("lambda")
                .orElseThrow().metadata.authors
                .flatMap { it.name.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    Dialog(onDismiss) {
        Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(
                bitmap = rememberTexture("textures/lambda_text_color.png"),
                contentDescription = "Lambda",
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            facts.forEach { Text(it) }
            Text("Authors", Modifier.padding(top = 6.dp), style = Type.title)
            authors.forEach { Text("•  $it") }
            Text(
                "Thanks to all community members",
                Modifier.padding(top = 2.dp),
                colors.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button("Copy Diagnostics") { mc.keyboard.clipboard = gatherDiagnostics() }
                Button("View License") {
                    Util.getOperatingSystem().open("$REPO_URL/blob/master/LICENSE.md")
                }
                Spacer(Modifier.weight(1f))
                Button("Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun LoaderPromptDialog(prompt: AutoUpdater.Prompt) {
    if (!LocalShown.current && prompt == AutoUpdater.Prompt.FirstLaunch) return
    val colors = LambdaTheme.colors
    Dialog(onDismiss = { AutoUpdater.answerPrompt(accepted = false) }) {
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (prompt) {
                AutoUpdater.Prompt.Uninstall -> {
                    Text("Uninstall Lambda Loader?", style = Type.title)
                    Text("This will close the client automatically once the uninstall is finished.")
                }
                AutoUpdater.Prompt.FirstLaunch, AutoUpdater.Prompt.Install -> {
                    Text("Switch to Lambda Loader?", style = Type.title)
                    Text("Lambda Loader replaces this version-specific Lambda jar with a lightweight bootstrap that keeps Lambda up to date.")
                    Text("•  Automatic updates on launch\n•  No manual jar replacement")
                    Text("If you choose to switch, Lambda will install the loader and close the game so changes apply on next start.")
                    Text(
                        "You can change this later from the AutoUpdater module.",
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Centered.VERTICALLY
            ) {
                Button(
                    label = when (prompt) {
                        AutoUpdater.Prompt.FirstLaunch -> "Keep Current Jar"
                        else -> "Cancel"
                    },
                    modifier = Modifier.weight(1f)
                ) { AutoUpdater.answerPrompt(accepted = false) }
                Button(
                    label =
                        if (prompt == AutoUpdater.Prompt.Uninstall) "Uninstall"
                        else "Switch To Loader",
                    modifier = Modifier.weight(1f)
                ) { AutoUpdater.answerPrompt(accepted = true) }
            }
        }
    }
}
