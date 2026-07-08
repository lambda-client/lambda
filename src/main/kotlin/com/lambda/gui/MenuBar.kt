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

package com.lambda.gui

import com.lambda.Lambda
import com.lambda.Lambda.REPO_URL
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.config.ConfigLoader
import com.lambda.config.ConfigLoader.configs
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.core.Loader
import com.lambda.event.EventFlow
import com.lambda.graphics.texture.TextureOwner.upload
import com.lambda.gui.DearImGui.EXTERNAL_LINK
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.gui.components.HudGuiLayout
import com.lambda.gui.components.QuickSearch
import com.lambda.gui.components.SettingsWidget.buildConfigSettingsContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGui.closeCurrentPopup
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.imgui.flag.ImGuiStyleVar
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.imgui.type.ImBoolean
import com.lambda.interaction.handlers.BaritoneHandler
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleRegistry.moduleNameMap
import com.lambda.module.tag.ModuleTag
import com.lambda.network.LambdaAPI
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.Diagnostics.gatherDiagnostics
import com.lambda.util.FolderRegistry
import com.lambda.util.FolderRegistry.minecraft
import com.mojang.blaze3d.platform.TextureUtil
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screen.DebugOptionsScreen
import net.minecraft.network.packet.c2s.play.ChangeGameModeC2SPacket
import net.minecraft.server.command.GameModeCommand
import net.minecraft.util.Util
import net.minecraft.world.GameMode
import java.util.*

object MenuBar {
    private var aboutRequested = false
    var newConfigName = ""
    val headerLogo = upload("textures/lambda_text_color.png")
    val lambdaLogo = upload("textures/lambda.png")
    val githubLogo = upload("textures/github_logo.png")

    var height = 0f

    fun ImGuiBuilder.buildMenuBar() {
        mainMenuBar {
            height = windowHeight

            lambdaMenu()
            menu("HUD") { buildHudMenu() }
            menu("GUI") { buildGuiMenu() }
            menu("Modules") { buildModulesMenu() }
            menu("Automation Configs") { buildAutomationConfigsMenu() }
            menu("Minecraft") { buildMinecraftMenu() }
            menu("Help") { buildHelpMenu() }
            buildGitHubReference()
        }

        if (aboutRequested) {
            ImGui.openPopup("About Lambda")
            aboutRequested = false
        }

        aboutPopup()
    }

    private fun ImGuiBuilder.lambdaMenu() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0)
        val opened = ImGui.beginMenu("Lam")

        val headerW = itemRectMaxX - itemRectMinX
        val headerH = itemRectMaxY - itemRectMinY

        val pad = 2f
        val lambdaIconSize = (headerH - pad * 2f).coerceAtLeast(1f)
        val iconX = itemRectMinX + (headerW - lambdaIconSize) * 0.5f
        val iconY = itemRectMinY + (headerH - lambdaIconSize) * 0.5f

        foregroundDrawList.addImage(
            lambdaLogo.id.toLong(),
            iconX, iconY,
            iconX + lambdaIconSize, iconY + lambdaIconSize
        )
        ImGui.popStyleColor()

        if (opened) {
            buildLambdaMenu()
            ImGui.endMenu()
        }
    }

    private fun ImGuiBuilder.buildLambdaMenu() {
        menu("Save Config...") {
            menuItem("Save All Configs") {
                ConfigLoader.configCategories.forEach { it.trySaveToFile(true) }
                info("Saved ${ConfigLoader.configCategories.size} configuration files.")
            }
            ConfigLoader.configCategories.forEach { config ->
                menuItem("Save ${config.name}") {
                    config.trySaveToFile(true)
                    info("Saved ${config.name}")
                }
            }
        }
        menu("Load Config...") {
            menuItem("Load All Configs") {
                ConfigLoader.configCategories.forEach { it.tryLoadFromFile() }
                info("Loaded ${ConfigLoader.configCategories.size} configuration files.")
            }
            ConfigLoader.configCategories.forEach { config ->
                menuItem("Load ${config.name}") {
                    config.tryLoadFromFile()
                    info("Loaded ${config.name}")
                }
            }
        }
        separator()
        menu("Settings") {
            menu("HUD Settings") {
                buildConfigSettingsContext(HudGuiLayout)
            }
            menu("GUI Settings") {
                buildConfigSettingsContext(ClickGuiLayout)
            }
            menu("Lambda API Settings") {
                buildConfigSettingsContext(LambdaAPI)
            }
            menu("Baritone Settings") {
                buildConfigSettingsContext(BaritoneHandler)
            }
        }
        separator()
        menu("Open Folder") {
            menuItem("Open Lambda Folder") {
                Util.getOperatingSystem().open(FolderRegistry.lambda)
            }
            menuItem("Open Config Folder") {
                Util.getOperatingSystem().open(FolderRegistry.config)
            }
            menuItem("Open Packet Logs Folder") {
                Util.getOperatingSystem().open(FolderRegistry.packetLogs)
            }
            menuItem("Open Replay Folder") {
                Util.getOperatingSystem().open(FolderRegistry.replay)
            }
            menuItem("Open Cache Folder") {
                Util.getOperatingSystem().open(FolderRegistry.cache)
            }
            menuItem("Open Capes Folder") {
                Util.getOperatingSystem().open(FolderRegistry.capes)
            }
            menuItem("Open Structures Folder") {
                Util.getOperatingSystem().open(FolderRegistry.structure)
            }
            menuItem("Open Maps Folder") {
                Util.getOperatingSystem().open(FolderRegistry.maps)
            }
        }
        separator()
        menuItem("New Profile...", enabled = false) {
            // ToDo (New Profile):
            //  - Open a modal "New Profile" with:
            //      [Profile Name] text input
            //      [Template] combo: Empty / Recommended Defaults / Copy from Current
            //      [Include HUD Layout] checkbox
            //  - On Create: instantiate and activate the profile, optionally copying values from current.
            //  - On Cancel: close modal with no changes.
        }
        menuItem("Import Profile...", enabled = false) {
            // ToDo (Import Profile):
            //  - Show a file picker for profile file(s).
            //  - Preview dialog: profile name, version, module count, settings count, includes HUD?
            //  - Provide options: Merge into Current / Replace Current.
            //  - Apply with progress/rollback on failure; toast result.
        }
        menuItem("Export Current Profile...", enabled = false) {
            // ToDo (Export Profile):
            //  - File save modal with checkboxes:
            //      [Include HUD Layout] [Include Keybinds] [Include Backups Metadata]
            //  - Create the export and toast result.
        }
        menu("Recent Profiles") {
            // ToDo (MRU Profiles):
            //  - Populate from a most-recently-used (MRU) list persisted in preferences.
            //  - On click: switch active profile (confirm if unsaved changes).
            menuItem("Example Profile", enabled = false) {}
        }
        separator()
        menu("Autosave Settings") {
            // ToDo:
            //  - Toggle autosave, set interval (1..60s), backup rotation count (0..20).
            menuItem("Autosave on changes", selected = true, enabled = false) {}
            menuItem("Autosave Interval: 10s", enabled = false) {}
            menuItem("Rotate Backups: 5", enabled = false) {}
        }
        menu("Backup & Restore") {
            // ToDo:
            //  - “Create Backup Now” and “Manage/Restore Backups” UIs; list with timestamps/comments.
            menuItem("Create Backup Now", enabled = false) {}
            menuItem("Restore From Backup...", enabled = false) {}
            menuItem("Manage Backups...", enabled = false) {}
        }
        menuItem("Profiles & Scopes...", enabled = false) {
            // ToDo (Profiles & Scopes Window):
            //  - Active Profile dropdown.
            //  - Scopes: Global / Per-Server / Per-World with enable overrides.
            //  - Show overridden-only list, origin badges, and precedence explanation.
        }
        separator()
        menuItem("About...") {
            aboutRequested = true
        }
        menuItem("Developer Mode", selected = ClickGuiLayout.developerMode) {
            ClickGuiLayout.developerMode = !ClickGuiLayout.developerMode
        }
        separator()
        menuItem("Close GUI", "Esc") { LambdaScreen.close() }
        menuItem("Exit Client") { mc.scheduleStop() }
    }

    private fun ImGuiBuilder.buildHudMenu() {
        menuItem(if (HudGuiLayout.isLocked) "Unlock" else "Lock") {
            HudGuiLayout.isLocked = !HudGuiLayout.isLocked
        }
        menuItem(if (HudGuiLayout.isShownInGUI) "Hide" else "Show") {
            HudGuiLayout.isShownInGUI = !HudGuiLayout.isShownInGUI
        }
        separator()
        menu("HUD Settings") {
            buildConfigSettingsContext(HudGuiLayout)
        }
    }

    private fun ImGuiBuilder.buildGuiMenu() {
        buildConfigSettingsContext(ClickGuiLayout)
    }

    private fun ImGuiBuilder.buildModulesMenu() {
        menu("Module Tag") {
            ModuleTag.defaults.forEach { tag ->
                checkbox(tag.name, ImBoolean(ModuleTag.isTagShown(tag))) {
                    ModuleTag.toggleTag(tag)
                }
            }
        }
        separator()
        // By Tag → toggle whether each module is shown in the ClickGui layout
        ModuleTag.defaults.forEach { tag ->
            menu(tag.name) {
                ModuleRegistry.modules
                    .filter { it.tag == tag }
                    .forEach { module ->
                        checkbox(module.name, module.showInClickGui::value)
                    }
            }
        }
    }

    private fun ImGuiBuilder.buildAutomationConfigsMenu() {
        button("New Config") { ImGui.openPopup("##new-config") }
        popupContextWindow("##new-config") {
            inputText("Name", ::newConfigName)
            button("Create") {
                if (newConfigName.isEmpty() && configs.none { it.name == newConfigName }) return@button
                UserAutomationConfig(newConfigName)
                newConfigName = ""
                closeCurrentPopup()
                return@button
            }
            sameLine()
            button("Cancel") {
                newConfigName = ""
                closeCurrentPopup()
            }
        }

        UserAutomationCategory.configs.forEach { config ->
            if (config !is UserAutomationConfig) throw IllegalStateException("All configs within UserAutomationConfigs must be UserAutomationConfigs!")
            buildAutomationConfigSelectable(config)
        }
        buildAutomationConfigSelectable(AutomationConfig.DEFAULT)
    }

    private fun ImGuiBuilder.buildAutomationConfigSelectable(config: AutomationConfig) {
	    ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, io.displaySize.y * 0.5f)
		menu(config.name) {
			if (config is UserAutomationConfig) {
				with(config.linkedModules) { buildLayout() }
				button("Delete") {
					config.linkedModules.value.forEach {
						moduleNameMap[it]?.let { module ->
							module.automationConfig = module.defaultAutomationConfig
						}
					}
					UserAutomationCategory.configs.remove(config)
				}
				separator()
			}
			buildConfigSettingsContext(config)
		}
    }

    private fun ImGuiBuilder.buildMinecraftMenu() {
        menu("Open Folder") {
            menuItem("Open Minecraft Folder") {
                Util.getOperatingSystem().open(minecraft)
            }
            menuItem("Open Saves Folder") {
                Util.getOperatingSystem().open(mc.runDirectory.toPath().toAbsolutePath().resolve("saves").toFile())
            }
            menuItem("Open Screenshots Folder") {
                Util.getOperatingSystem().open(mc.runDirectory.toPath().toAbsolutePath().resolve("screenshots").toFile())
            }
            menuItem("Open Resource Packs Folder") {
                Util.getOperatingSystem().open(mc.runDirectory.toPath().toAbsolutePath().resolve("resourcepacks").toFile())
            }
            menuItem("Open Mods Folder") {
                Util.getOperatingSystem().open(mc.runDirectory.toPath().toAbsolutePath().resolve("mods").toFile())
            }
        }
        separator()
        runSafe {
            menu("Gamemode", enabled = GameModeCommand.PERMISSION_CHECK.allows(player.permissions)) {
                menuItem("Survival", selected = interaction.gameMode == GameMode.SURVIVAL) {
                    connection.sendPacket(ChangeGameModeC2SPacket(GameMode.SURVIVAL))
                }
                menuItem("Creative", selected = interaction.gameMode == GameMode.CREATIVE) {
                    connection.sendPacket(ChangeGameModeC2SPacket(GameMode.CREATIVE))
                }
                menuItem("Adventure", selected = interaction.gameMode == GameMode.ADVENTURE) {
                    connection.sendPacket(ChangeGameModeC2SPacket(GameMode.ADVENTURE))
                }
                menuItem("Spectator", selected = interaction.gameMode == GameMode.SPECTATOR) {
                    connection.sendPacket(ChangeGameModeC2SPacket(GameMode.SPECTATOR))
                }
            }
            menu("Debug Menu") {
                menuItem("Show Advanced Tooltips", "F3+H", mc.options.advancedItemTooltips) {
                    mc.options.advancedItemTooltips = !mc.options.advancedItemTooltips
                    mc.options.write()
                }
                menuItem("Copy Location (as command)", "F3+C") {
                    val cmd = String.format(
                        Locale.ROOT,
                        "/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
                        world.registryKey.value,
                        player.x, player.y, player.z, player.yaw, player.pitch
                    )
                    ImGui.setClipboardText(cmd)
                    info("Copied location command to clipboard.")
                }
                menuItem("Clear Chat", "F3+D") {
                    mc.inGameHud?.chatHud?.clear(false)
                }
                menuItem("Open Debug Entry Menu", "(new)") { // ToDo: Put actual keybind in
                    mc.setScreen(DebugOptionsScreen())
                }

                separator()

                menuItem(
                    label = "Pause On Lost Focus",
                    shortcut = "F3+Esc",
                    selected = mc.options.pauseOnLostFocus
                ) {
                    mc.options.pauseOnLostFocus = !mc.options.pauseOnLostFocus
                    mc.options.write()
                    info("Pause on lost focus ${if (mc.options.pauseOnLostFocus) "enabled" else "disabled"}.")
                }

                separator()

                menuItem("Reload Resource Packs", "F3+T") {
                    info("Reloading resource packs...")
                    mc.reloadResources()
                }

                menuItem("Reload Chunks", "F3+A") {
                    mc.worldRenderer.reload()
                }

                separator()

                menuItem("Show Debug Menu", "F3", mc.debugHudEntryList.isF3Enabled) {
                    mc.debugHudEntryList.toggleF3Enabled()
                }
                menuItem("Rendering Chart", "F3+1", mc.debugHud.renderingChartVisible) {
                    mc.debugHud.toggleRenderingChart()
                }
                menuItem("Rendering & Tick Charts", "F3+2", mc.debugHud.renderingAndTickChartsVisible) {
                    mc.debugHud.toggleRenderingAndTickCharts()
                }
                menuItem("Packet Size & Ping Charts", "F3+3", mc.debugHud.packetSizeAndPingChartsVisible) {
                    mc.debugHud.togglePacketSizeAndPingCharts()
                }

                separator()

                menuItem("Start/Stop Profiler", "F3+L") {
                    mc.toggleDebugProfiler { message ->
                        info(message)
                    }
                }
                menuItem("Dump Dynamic Textures", "F3+S") {
                    val root = mc.runDirectory.toPath().toAbsolutePath()
                    val output = TextureUtil.getDebugTexturePath(root)
                    mc.textureManager.dumpDynamicTextures(output)
                    info("Dumped dynamic textures to: ${root.relativize(output)}")
                }
            }
        } ?: menuItem("Debug (only available ingame)", enabled = false)
    }

    private fun ImGuiBuilder.buildHelpMenu() {
        menuItem("Quick Search...", "Shift+Shift") {
            QuickSearch.open()
        }
        menuItem("Documentation $EXTERNAL_LINK") {
            Util.getOperatingSystem().open("$REPO_URL/wiki")
        }
        menuItem("Report Issue $EXTERNAL_LINK") {
            mc.keyboard.clipboard = gatherDiagnostics()
            info("Copied diagnostics to clipboard. Please paste it in a new issue on GitHub and click “Submit new issue”. Thank you!")
            Util.getOperatingSystem().open("$REPO_URL/issues")
        }
        menuItem("Check for Updates $EXTERNAL_LINK") {
            // ToDo:
            //  - Check for a newer version, show availability & changelog, and allow opening release page.
            //  - Needs UpdateManager
            Util.getOperatingSystem().open("$REPO_URL/releases")
        }
    }

    private fun ImGuiBuilder.aboutPopup() {
        popupModal("About Lambda", ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoTitleBar) {
            imageHorizontallyCentered(headerLogo.id.toLong(), 553f, 200f)
            group {
                text("Version: ${Lambda.VERSION}")
                if (Lambda.isDebug) text("Development Environment")
                text("Runtime: ${Loader.runtime}")
                text("Modules: ${ModuleRegistry.modules.size}")
                text("Commands: ${CommandRegistry.commands.size}")
                val totalSettings = ConfigLoader.configCategories.sumOf { cfg ->
                    cfg.configs.sumOf {
                        var count = 0
                        it.settingLayers.forEachEntry { _, _ -> count++ }
                        count
                    }
                }
                text("Settings: $totalSettings")
                text("Synchronous listeners: ${EventFlow.syncListeners.size}")
                text("Concurrent listeners: ${EventFlow.concurrentListeners.size}")
            }
            separator()
            text("Authors")
            FabricLoader.getInstance().getModContainer("lambda")
                .orElseThrow { IllegalStateException("Could not find Lambda mod container!") }
                .metadata
                .authors
                .forEach { author ->
                    if (author.name.isEmpty()) return@forEach
                    author.name.split(",").forEach { name ->
                        bulletText(name.trim())
                    }
                }
            text("Thanks to all community members")

            separator()
            group {
                button("Copy Diagnostics") {
                    ImGui.setClipboardText(gatherDiagnostics())
                }
                sameLine()
                button("View License $EXTERNAL_LINK") {
                    Util.getOperatingSystem().open("$REPO_URL/blob/master/LICENSE.md")
                }
                sameLine()
                button("Close") {
                    aboutRequested = false
                    closeCurrentPopup()
                }
            }
        }
    }

    private fun ImGuiBuilder.buildGitHubReference() {
        val frameH = frameHeight - 2f
        val iconSize = (frameH - 6f).coerceAtLeast(14f)
        val spacingPx = 8f

        sameLine()
        cursorPosX = windowContentRegionMaxX - iconSize - spacingPx

        withStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f) {
            withStyleColor(ImGuiCol.Button, 0x00000000) {
                withStyleColor(ImGuiCol.ButtonHovered, 0x22FFFFFF) {
                    withStyleColor(ImGuiCol.ButtonActive, 0x44FFFFFF) {
                        val clicked = ImGui.imageButton("##github", githubLogo.id.toLong(), iconSize, iconSize)
                        lambdaTooltip("Open GitHub Repository $EXTERNAL_LINK")
                        if (clicked) {
                            Util.getOperatingSystem().open(REPO_URL)
                        }
                    }
                }
            }
        }
    }
}
