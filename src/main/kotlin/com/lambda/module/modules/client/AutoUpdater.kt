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

package com.lambda.module.modules.client

import com.lambda.Lambda.mc
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.LambdaScreen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.dsl.ImGuiBuilder.popupModal
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.threading.runIO
import com.lambda.util.CommunicationUtils.debug
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.CommunicationUtils.warn
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import java.net.URI
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

object AutoUpdater : Module(
    name = "AutoUpdater",
    description = "Installs / uninstalls Lambda loader",
    tag = ModuleTag.CLIENT,
) {
    private val debug by setting("Debug", false, "Enable debug logging")
    private val loaderBranch by setting("Loader Branch", Branch.Stable, "Select loader update branch")
    private val clientBranch by setting("Client Branch", Branch.Dev, "Select client update branch")

    private var loaderPromptHandled by property(false)

    @JvmStatic var showFirstLaunchModal = false
    @JvmStatic var showInstallModal = false
    @JvmStatic var showUninstallModal = false
    private var firstLaunchStateInitialized = false

    private const val MAVEN_URL = "https://maven.lambda-client.org"
    private const val LOADER_STABLE_META = "$MAVEN_URL/stable/com/lambda/lambda-loader/maven-metadata.xml"
    private const val LOADER_DEV_META = "$MAVEN_URL/dev/com/lambda/lambda-loader/maven-metadata.xml"
    private const val CLIENT_STABLE_META = "$MAVEN_URL/stable/com/lambda/lambda/maven-metadata.xml"
    private const val CLIENT_DEV_META = "$MAVEN_URL/dev/com/lambda/lambda/maven-metadata.xml"

    private enum class Branch {
        Stable,
        Dev
    }

    const val WINDOW_FLAGS =
        ImGuiWindowFlags.AlwaysAutoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoScrollbar or
                ImGuiWindowFlags.NoScrollWithMouse

    init {
        onEnableUnsafe {
            if (mc.currentScreen is LambdaScreen && !showUninstallModal)
                showInstallModal = true
            showUninstallModal = false
        }

        onDisableUnsafe {
            if (mc.currentScreen is LambdaScreen && !showInstallModal)
                showUninstallModal = true
            showInstallModal = false
        }

        listen<GuiEvent.NewImguiFrame>(alwaysListen = true) {
            initializeFirstLaunchStateIfNeeded()

            if (showFirstLaunchModal) {
                if (mc.currentScreen !is LambdaScreen) return@listen

                ImGui.openPopup("Loader Installation Wizard")
                popupModal("Loader Installation Wizard", WINDOW_FLAGS) {
                    renderLoaderInstallExplanation()

                    val buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().itemSpacing.x) / 2f

                    button("Switch To Loader", buttonWidth, 0f) {
                        completeFirstLaunchPrompt()
                        showInstallModal = false
                        showUninstallModal = false
                        installLoader()
                    }

                    sameLine()

                    button("Keep Current Jar", buttonWidth, 0f) {
                        completeFirstLaunchPrompt()
                    }
                }
                return@listen
            }

            showFirstLaunchModal = false

            if (showInstallModal) {
                ImGui.openPopup("Loader Installation Wizard")
                popupModal("Loader Installation Wizard", WINDOW_FLAGS) {
                    renderLoaderInstallExplanation()

                    val buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().itemSpacing.x) / 2f

                    button("Switch To Loader", buttonWidth, 0f) {
                        installLoader()
                        showInstallModal = false
                    }

                    sameLine()

                    button("Cancel", buttonWidth, 0f) {
                        disable()
                    }
                }
                return@listen
            }

            showInstallModal = false

            if (showUninstallModal) {
                ImGui.openPopup("Uninstall Loader")
                popupModal("Uninstall Loader", WINDOW_FLAGS) {
                    text("Do you want to uninstall Lambda Loader?")
                    separator()
                    text("This will close the client automatically once the uninstall is finished.")
                    spacing()

                    button("Uninstall", 120f, 0f) {
                        installClient()
                        showUninstallModal = false
                    }

                    sameLine()

                    button("Cancel", 120f, 0f) {
                        enable()
                    }
                }
                return@listen
            }

            showUninstallModal = false
        }
    }

    private fun installLoader() {
        runIO {
            try {
                debug("Starting Lambda Loader install...")

                val loaderJar = downloadLatestLoader()
                if (loaderJar == null) {
                    logError("Failed to download latest Lambda loader")
                    return@runIO
                }

                val clientJarPath = getModJarPath("lambda")
                if (debug) debug("Lambda client JAR path: $clientJarPath")

                val jarFile = clientJarPath.toFile()
                jarFile.writeBytes(loaderJar)

                debug("Successfully installed Lambda loader! Restarting...")

                mc.stop()
            } catch (e: Exception) {
                disable()
                logError("Error installing Lambda loader", e)
            }
        }
    }

    private fun installClient() {
        runIO {
            try {
                debug("Starting Lambda client install...")

                val clientJar = downloadLatestClient()
                if (clientJar == null) {
                    logError("Failed to download latest Lambda client")
                    return@runIO
                }

                val loaderJarPath = getModJarPath("lambda-loader")
                if (debug) debug("Lambda loader JAR path: $loaderJarPath")

                val jarFile = loaderJarPath.toFile()
                jarFile.writeBytes(clientJar)

                debug("Successfully installed Lambda client! Restarting...")

                mc.stop()
            } catch (e: Exception) {
                enable()
                logError("Error installing Lambda client", e)
            }
        }
    }

    fun downloadLatestLoader(): ByteArray? =
        try {
            val branch = loaderBranch
            val mcVersion = getMinecraftVersion()

            if (debug) debug("Downloading loader for MC $mcVersion from ${branch.name} branch")

            var version: String?
            var baseUrl: String?

            when (branch) {
                Branch.Stable -> {
                    version = fetchLatestVersion(LOADER_STABLE_META, null)
                    baseUrl = "$MAVEN_URL/stable"
                }
                Branch.Dev -> {
                    version = fetchLatestVersion(LOADER_DEV_META, null)
                    baseUrl = "$MAVEN_URL/dev"
                }
            }

            if (version == null && branch == Branch.Stable) {
                warn("No stable loader found, falling back to dev")
                version = fetchLatestVersion(LOADER_DEV_META, null)
                baseUrl = "$MAVEN_URL/dev"
            }

            if (version == null) {
                logError("No loader version found")
                return null
            }

            val jarUrl = "$baseUrl/com/lambda/lambda-loader/$version/lambda-loader-$version.jar"

            if (debug) debug("Downloading from: $jarUrl")

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logError("Failed to download loader", e)
            null
        }

    fun downloadLatestClient(): ByteArray? =
        try {
            val branch = clientBranch
            val mcVersion = getMinecraftVersion()

            if (debug) debug("Downloading client for MC $mcVersion from ${branch.name} branch")

            var version: String?
            var baseUrl: String?

            when (branch) {
                Branch.Stable -> {
                    version = fetchLatestVersion(CLIENT_STABLE_META, mcVersion)
                    baseUrl = "$MAVEN_URL/stable"
                }
                Branch.Dev -> {
                    version = fetchLatestVersion(CLIENT_DEV_META, mcVersion)
                    baseUrl = "$MAVEN_URL/dev"
                }
            }

            if (version == null && branch == Branch.Stable) {
                warn("No stable client found for MC $mcVersion, falling back to dev")
                version = fetchLatestVersion(CLIENT_DEV_META, mcVersion)
                baseUrl = "$MAVEN_URL/dev"
            }

            if (version == null) {
                logError("No client version found for MC $mcVersion")
                return null
            }

            val jarUrl = "$baseUrl/com/lambda/lambda/$version/lambda-$version.jar"

            if (debug) debug("Downloading from: $jarUrl")

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logError("Failed to download client", e)
            null
        }

    private fun fetchLatestVersion(url: String, mcVersion: String? = null): String? =
        try {
            val xml = URI(url).toURL().readText()
            parseLatestVersion(xml, mcVersion)
        } catch (_: java.io.FileNotFoundException) {
            if (debug) warn("Metadata not found at $url (repo might be empty)")
            null
        } catch (e: Exception) {
            if (debug) logError("Error fetching metadata from $url", e)
            null
        }

    private fun parseLatestVersion(xml: String, mcVersion: String? = null): String? =
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            (0 until versionNodes.length).forEach  { i ->
                versions.add(versionNodes.item(i).textContent)
            }

            if (debug) {
                if (mcVersion != null) {
                    debug("Target MC version: $mcVersion")
                }
                debug("Available versions: ${versions.joinToString(", ")}")
            }

            val matchingVersions = if (mcVersion != null) {
                versions.filter { version ->
                    val mcVersionInArtifact = version.substringAfter("+").substringBefore("-")
                    val normalizedArtifact = mcVersionInArtifact.replace(".", "")
                    val normalizedTarget = mcVersion.replace(".", "")
                    normalizedArtifact == normalizedTarget
                }
            } else {
                versions
            }

            if (matchingVersions.isEmpty()) {
                if (debug) {
                    val versionMsg = mcVersion?.let { "for MC $it" } ?: ""
                    warn("No versions found $versionMsg")
                }
                null
            } else matchingVersions.last()
        } catch (e: Exception) {
            logError("Error parsing version", e)
            null
        }

    private fun getMinecraftVersion() = SharedConstants.getGameVersion().name()

    private fun getModJarPath(modId: String): Path {
        val fabricLoader = FabricLoader.getInstance()
        val modContainer = fabricLoader.getModContainer(modId)
        return modContainer.get().origin.paths[0].toAbsolutePath()
    }

    private fun initializeFirstLaunchStateIfNeeded() {
        if (firstLaunchStateInitialized) return
        firstLaunchStateInitialized = true

        val usingLoader = FabricLoader.getInstance().isModLoaded("lambda-loader")
        if (usingLoader) {
            completeFirstLaunchPrompt()
            if (!isEnabled) enable()
            return
        }

        showFirstLaunchModal = !loaderPromptHandled
    }

    private fun ImGuiBuilder.renderLoaderInstallExplanation() {
        text("Switch to Lambda Loader?")
        separator()
        spacing()
        text("Lambda Loader replaces this version-specific Lambda jar")
        text("with a lightweight bootstrap that keeps Lambda up to date.")
        spacing()
        text("Benefits:")
        text("- Automatic updates on launch")
        text("- No manual jar replacement")
        spacing()
        text("If you choose to switch, Lambda will install the loader")
        text("and close the game so changes apply on next start.")
        spacing()
        separator()
        text("You can change this later from the AutoUpdater module.")
        spacing()
    }

    @JvmStatic
    fun dismissFirstLaunchPrompt() {
        completeFirstLaunchPrompt()
    }

    private fun completeFirstLaunchPrompt() {
        loaderPromptHandled = true
        showFirstLaunchModal = false
    }
}
