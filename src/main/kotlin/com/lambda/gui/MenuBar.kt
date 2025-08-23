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

package com.lambda.gui

import com.lambda.Lambda
import com.lambda.Lambda.REPO_URL
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.config.Configuration
import com.lambda.core.Loader
import com.lambda.event.EventFlow
import com.lambda.graphics.texture.TextureOwner.upload
import com.lambda.gui.DearImGui.EXTERNAL_LINK
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.Diagnostics.gatherDiagnostics
import com.lambda.util.FolderRegister
import com.mojang.blaze3d.platform.TextureUtil
import imgui.ImGui
import imgui.ImGui.closeCurrentPopup
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Util
import net.minecraft.world.GameMode
import java.util.Locale

object MenuBar {
    private var aboutRequested = false
    val headerLogo = upload("textures/lambda_text_color.png")
    val lambdaLogo = upload("textures/lambda.png")
    val githubLogo = upload("textures/github_logo.png")

    // ToDo: On pressing shift (or something else) open a quick search bar popup.
    //  - Search for modules, hud elements, and commands using levenshtein distance.
    private val quickSearch = ImString(64)

    fun ImGuiBuilder.buildMenuBar() {
        mainMenuBar {
            lambdaMenu()
            menu("HUD") { buildHudMenu() }
            menu("Modules") { buildModulesMenu() }
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
        menuItem("New Profile...") {
            // ToDo (New Profile):
            //  - Open a modal "New Profile" with:
            //      [Profile Name] text input
            //      [Template] combo: Empty / Recommended Defaults / Copy from Current
            //      [Include HUD Layout] checkbox
            //  - On Create: instantiate and activate the profile, optionally copying values from current.
            //  - On Cancel: close modal with no changes.
        }
        menuItem("Open Config Folder") {
            Util.getOperatingSystem().open(FolderRegister.config)
        }
        separator()
        menuItem("Save All Configs", "Ctrl+S") {
            // Save every configuration file and show a toast with the total count.
            Configuration.configurations.forEach { it.trySave(true) }
            runSafe { info("Saved ${Configuration.configurations.size} configuration files.") }
        }
        menuItem("Load All Configs", "Ctrl+L") {
            // Load every configuration file and show a toast with the total count.
            Configuration.configurations.forEach { it.tryLoad() }
            runSafe { info("Loaded ${Configuration.configurations.size} configuration files.") }
        }
        separator()
        menuItem("Import Profile...") {
            // ToDo (Import Profile):
            //  - Show a file picker for profile file(s).
            //  - Preview dialog: profile name, version, module count, settings count, includes HUD?
            //  - Provide options: Merge into Current / Replace Current.
            //  - Apply with progress/rollback on failure; toast result.
        }
        menuItem("Export Current Profile...") {
            // ToDo (Export Profile):
            //  - File save modal with checkboxes:
            //      [Include HUD Layout] [Include Keybinds] [Include Backups Metadata]
            //  - Create the export and toast result.
        }
        menu("Recent Profiles") {
            // ToDo (MRU Profiles):
            //  - Populate from a most-recently-used (MRU) list persisted in preferences.
            //  - On click: switch active profile (confirm if unsaved changes).
            menuItem("Example Profile") {}
        }
        menuItem("Save All", "Ctrl+S") {
            Configuration.configurations.forEach { it.trySave(true) }
            runSafe { info("Saved ${Configuration.configurations.size} configuration files.") }
        }
        menuItem("Load All", "Ctrl+L") {
            Configuration.configurations.forEach { it.tryLoad() }
            runSafe { info("Loaded ${Configuration.configurations.size} configuration files.") }
        }
        separator()
        menu("Autosave Settings") {
            // ToDo:
            //  - Toggle autosave, set interval (1..60s), backup rotation count (0..20).
            menuItem("Autosave on changes", selected = true) {}
            menuItem("Autosave Interval: 10s") {}
            menuItem("Rotate Backups: 5") {}
        }
        menu("Backup & Restore") {
            // ToDo:
            //  - “Create Backup Now” and “Manage/Restore Backups” UIs; list with timestamps/comments.
            menuItem("Create Backup Now") {}
            menuItem("Restore From Backup...") {}
            menuItem("Manage Backups...") {}
        }
        menuItem("Profiles & Scopes...") {
            // ToDo (Profiles & Scopes Window):
            //  - Active Profile dropdown.
            //  - Scopes: Global / Per-Server / Per-World with enable overrides.
            //  - Show overridden-only list, origin badges, and precedence explanation.
        }
        menuItem("Module Settings Inspector...") {
            // ToDo (Settings Inspector Window):
            //  - Left: Tree (Tag → Module → Group).
            //  - Right: Settings editor with search; filters (Changed-only, Overridden-only, Advanced).
            //  - Reset group/module actions.
        }
        menuItem("Placement/Build Settings...") {
            // ToDo (Placement Panel):
            //  - Rotate For Place, Air Place Mode, Axis Rotate (conditional),
            //  - Place Stage Mask (multi-select), Place Confirmation Mode,
            //  - Max Pending Placements, Places Per Tick,
            //  - Swing On Place + Swing Type, Place Sounds.
            //  - Provide concise tooltips for trade-offs.
        }
        menuItem("Inventory Settings...") {
            // ToDo (Inventory Panel):
            //  - Container group: Disposables editor (list add/remove + defaults), Swap with Disposables,
            //    Provider/Store Priorities.
            //  - Access group: Access Shulkers/Ender/Chests/Stashes toggles.
            //  - “Test Access” helper to simulate lookups.
        }
        separator()
        menuItem("About...") {
            aboutRequested = true
        }
        separator()
        menuItem("Close GUI", "Esc") { LambdaScreen.close() }
        menuItem("Exit Client") { mc.scheduleStop() }
    }

    private fun ImGuiBuilder.buildViewMenu() {

        separator()
        menu("UI Scale") {
            // ToDo:
            //  - Apply selected scale (100/125/150/175/200%), update fonts via DearImGui.updateScale-like method.
            listOf("100%", "125%", "150%", "175%", "200%").forEach { label ->
                menuItem(label, selected = (label == "125%")) { /* set scale & rebuild fonts */ }
            }
        }
    }

    private fun ImGuiBuilder.buildHudMenu() {
        menuItem("Copy HUD Layout") {
            // ToDo:
            //  - Serialize current HUD widget tree with positions/anchors/safe-margins to memory clipboard.
        }
        menuItem("Paste HUD Layout") {
            // ToDo:
            //  - Deserialize from clipboard and apply; if incompatible, show a non-blocking warning.
        }
        menuItem("Reset to Defaults") {
            // ToDo:
            //  - Reset the currently focused panel’s settings to defaults (confirmation modal).
        }
        separator()
        menuItem("Keybind Manager...") {
            // ToDo (Keybind Manager Window):
            //  - Panel with search/filter; table columns: Action/Module | Current Key | Conflict | Change | Clear
            //  - Conflict detector with "Auto-resolve" suggestions.
        }
        menuItem("Open Editor", "Ctrl+Alt+C") {
            // ToDo (HUD Editor Window):
            //  - Full-screen canvas with grid; left "Elements" list; right "Properties" inspector.
            //  - Drag & drop, snap grid, lock/unlock, safe margins, anchors, multi-select & alignment tools.
        }
        menu("Add Widget") {
            // ToDo:
            //  - Populate from available HUD widgets. On click, add centered and select for property editing.
            menuItem("Stats") {}
            menuItem("Clock") {}
            menuItem("Ping") {}
            menuItem("Coordinates") {}
            menuItem("Module List") {}
        }
        menu("Layouts") {
            // ToDo:
            //  - New/Save/Save As/Load/Import/Export layout actions; Toggle "Autosave on change".
            menuItem("New...") {}
            menuItem("Save") {}
            menuItem("Save As...") {}
            menuItem("Load...") {}
            menuItem("Import...") {}
            menuItem("Export...") {}
            separator()
            menuItem("Autosave on change", selected = true) {}
        }
        menuItem("Reset Layout") {
            // ToDo:
            //  - Confirm and restore the default HUD layout.
        }
        menuItem("Toggle Edit Handles", selected = true) {
            // ToDo:
            //  - Show/hide bounds, anchors, labels while in edit mode.
        }
    }

    private fun ImGuiBuilder.buildModulesMenu() {
        menu("Module Tag") {
            ModuleTag.defaults.forEach { tag ->
                menuItem(tag.name, selected = ModuleTag.isTagShown(tag)) {
                    ModuleTag.toggleTag(tag)
                }
            }
        }
        separator()
        // By Tag → quick enable/disable per module
        ModuleTag.defaults.forEach { tag ->
            menu(tag.name) {
                ModuleRegistry.modules
                    .filter { it.tag == tag }
                    .sortedBy { it.name.lowercase() }
                    .forEach { module ->
                        menuItem(module.name, selected = module.isEnabled) {
                            if (module.isEnabled) module.disable() else module.enable()
                        }
                        // Optionally, offer a "Settings..." item to focus this module’s details UI.
                    }
            }
        }
    }

    private fun ImGuiBuilder.buildMinecraftMenu() {
        menu("Open Folder") {
            menuItem("Open Minecraft Folder") {
                Util.getOperatingSystem().open(FolderRegister.minecraft)
            }
            menuItem("Open Lambda Folder") {
                Util.getOperatingSystem().open(FolderRegister.lambda)
            }
            menuItem("Open Config Folder") {
                Util.getOperatingSystem().open(FolderRegister.config)
            }
            menuItem("Open Cache Folder") {
                Util.getOperatingSystem().open(FolderRegister.cache)
            }
            menuItem("Open Capes Folder") {
                Util.getOperatingSystem().open(FolderRegister.capes)
            }
            menuItem("Open Structures Folder") {
                Util.getOperatingSystem().open(FolderRegister.structure)
            }
            menuItem("Open Maps Folder") {
                Util.getOperatingSystem().open(FolderRegister.maps)
            }
        }
        separator()
        runSafe {
            menu("Gamemode", enabled = player.hasPermissionLevel(2)) {
                menuItem("Survival", selected = interaction.gameMode == GameMode.SURVIVAL) {
                    connection.sendCommand("gamemode survival")
                }
                menuItem("Creative", selected = interaction.gameMode == GameMode.CREATIVE) {
                    connection.sendCommand("gamemode creative")
                }
                menuItem("Adventure", selected = interaction.gameMode == GameMode.ADVENTURE) {
                    connection.sendCommand("gamemode adventure")
                }
                menuItem("Spectator", selected = interaction.gameMode == GameMode.SPECTATOR) {
                    connection.sendCommand("gamemode spectator")
                }
            }
            menu("Debug Menu") {
                menuItem(
                    "Show Debug Menu", "F3",
                    mc.debugHud.showDebugHud
                ) { mc.debugHud.toggleDebugHud() }
                menuItem(
                    "Rendering Chart", "F3+1",
                    mc.debugHud.renderingChartVisible
                ) { mc.debugHud.toggleRenderingChart() }
                menuItem(
                    "Rendering & Tick Charts", "F3+2",
                    mc.debugHud.renderingAndTickChartsVisible
                ) { mc.debugHud.toggleRenderingAndTickCharts() }
                menuItem(
                    "Packet Size & Ping Charts", "F3+3",
                    mc.debugHud.packetSizeAndPingChartsVisible
                ) { mc.debugHud.togglePacketSizeAndPingCharts() }

                separator()

                menuItem("Reload Chunks", "F3+A") {
                    mc.worldRenderer.reload()
                }
                menuItem(
                    "Show Chunk Borders", "F3+G",
                    mc.debugRenderer.showChunkBorder
                ) { mc.debugRenderer.toggleShowChunkBorder() }
                menuItem("Show Octree", selected = mc.debugRenderer.showOctree) {
                    mc.debugRenderer.toggleShowOctree()
                }
                menuItem(
                    label = "Show Hitboxes",
                    shortcut = "F3+B",
                    selected = mc.entityRenderDispatcher.shouldRenderHitboxes()
                ) {
                    val now = !mc.entityRenderDispatcher.shouldRenderHitboxes()
                    mc.entityRenderDispatcher.setRenderHitboxes(now)
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

                separator()

                menuItem(
                    label = "Advanced Tooltips",
                    shortcut = "F3+H",
                    selected = mc.options.advancedItemTooltips
                ) {
                    mc.options.advancedItemTooltips = !mc.options.advancedItemTooltips
                    mc.options.write()
                }
                menuItem("Inspect (Copy Look At)", "F3+I") {
                    // TODO: Implement precise copyLookAt(hasOp = player.hasPermissionLevel(2), raycastBlocksIfNotShift = !Screen.hasShiftDown())
                    info("Inspect: Not yet implemented.")
                }

                separator()

                menuItem("Start/Stop Profiler", "F3+L") {
                    // TODO: Wire mc.toggleDebugProfiler with callback logging
                    info("Profiler control: Not yet implemented.")
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

                menuItem("Dump Dynamic Textures", "F3+S") {
                    val root = mc.runDirectory.toPath().toAbsolutePath()
                    val output = TextureUtil.getDebugTexturePath(root)
                    mc.textureManager.dumpDynamicTextures(output)
                    info("Dumped dynamic textures to: ${root.relativize(output)}")
                }
                menuItem("Reload Resource Packs", "F3+T") {
                    info("Reloading resource packs...")
                    mc.reloadResources()
                }
            }
        } ?: menuItem("Debug (only available ingame)", enabled = false)
    }

    private fun ImGuiBuilder.buildHelpMenu() {
        menuItem("Quick Search...") {
            // ToDo:
            //  - Search for modules, commands, and HUD widgets.
            //  - Show matches in a search panel below the GUI.
            //  - Support regex.
            //  - Support levenshtein distance.
            //  - Support multiple search terms.
            //  - Support search history.
            //  - Support search filters (by type, enabled/disabled, etc).
            //  - Support search scopes (all/enabled/disabled).
            //  - Support search shortcuts (Ctrl+F, Cmd+F, etc).
            //  - Show match count in the search panel.
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
                val totalSettings = Configuration.configurations.sumOf { cfg ->
                    cfg.configurables.sumOf { it.settings.size }
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