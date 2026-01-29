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
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.gui.dsl.ImGuiBuilder.buildLayout
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

object AutoUpdater : Module(
    name = "AutoUpdater",
    description = "Installs / uninstalls Lambda loader",
    tag = ModuleTag.CLIENT,
) {
    private val logger = LoggerFactory.getLogger("AutoUpdater")

    private val debug by setting("Debug", false, "Enable debug logging")
    private val loaderBranch by setting("Loader Branch", Branch.Stable, "Select loader update branch")
    private val clientBranch by setting("Client Branch", Branch.Snapshot, "Select client update branch")

    var showInstallModal = false
        private set
    var showUninstallModal = false
        private set

    private const val MAVEN_URL = "https://maven.lambda-client.org"
    private const val LOADER_RELEASES_META = "$MAVEN_URL/releases/com/lambda/loader/maven-metadata.xml"
    private const val LOADER_SNAPSHOTS_META = "$MAVEN_URL/snapshots/com/lambda/loader/maven-metadata.xml"
    private const val CLIENT_RELEASES_META = "$MAVEN_URL/releases/com/lambda/lambda/maven-metadata.xml"
    private const val CLIENT_SNAPSHOTS_META = "$MAVEN_URL/snapshots/com/lambda/lambda/maven-metadata.xml"

    private enum class Branch {
        Stable,
        Snapshot
    }

    init {
        onEnable {
            if (mc.currentScreen is LambdaScreen && ClickGuiLayout.open)
                showInstallModal = true
        }

        onDisable {
            if (mc.currentScreen is LambdaScreen && ClickGuiLayout.open)
                showUninstallModal = true
        }

        listen<GuiEvent.NewFrame>(alwaysListen = true) {
            if (showInstallModal) {
                buildLayout {
                    openPopup("install-lambda-loader")
                    popupModal("install-lambda-loader", ImGuiWindowFlags.AlwaysAutoResize) {
                        text("Do you want to install Lambda Client?")
                        ImGui.spacing()
                        text("This will close the client automatically once the install is finished.")
                        ImGui.spacing()

                        button("Install", 120f, 0f) {
                            showInstallModal = false
                            installLoader()
                        }

                        sameLine()

                        button("Cancel", 120f, 0f) {
                            showInstallModal = false
                        }
                    }
                }
                return@listen
            }

            if (showUninstallModal) {
                buildLayout {
                    openPopup("uninstall-lambda-loader")
                    popupModal("uninstall-lambda-loader", ImGuiWindowFlags.AlwaysAutoResize) {
                        text("Do you want to uninstall Lambda Loader?")
                        ImGui.spacing()
                        text("This will close the client automatically once the uninstall is finished.")
                        ImGui.spacing()

                        button("Uninstall", 120f, 0f) {
                            showUninstallModal = false
                            installClient()
                        }

                        sameLine()

                        button("Cancel", 120f, 0f) {
                            showUninstallModal = false
                        }
                    }
                }
            }
        }
    }

    private fun installLoader() {
        runBlocking(Dispatchers.IO) {
            try {
                logger.info("Starting Lambda loader install...")

                val loaderJar = downloadLatestLoader()
                if (loaderJar == null) {
                    logger.error("Failed to download latest Lambda loader")
                    return@runBlocking
                }

                val clientJarPath = getModJarPath("lambda")
                if (debug) logger.info("Lambda client JAR path: $clientJarPath")

                val jarFile = clientJarPath.toFile()
                jarFile.writeBytes(loaderJar)

                logger.info("Successfully installed Lambda loader! Restarting...")

                mc.stop()
            } catch (e: Exception) {
                logger.error("Error installing Lambda loader", e)
            }
        }
    }

    private fun installClient() {
        runBlocking(Dispatchers.IO) {
            try {
                logger.info("Starting Lambda client install...")

                val clientJar = downloadLatestClient()
                if (clientJar == null) {
                    logger.error("Failed to download latest Lambda client")
                    return@runBlocking
                }

                val loaderJarPath = getModJarPath("lambda-loader")
                if (debug) logger.info("Lambda loader JAR path: $loaderJarPath")

                val jarFile = loaderJarPath.toFile()
                jarFile.writeBytes(clientJar)

                logger.info("Successfully installed Lambda client! Restarting...")

                mc.stop()
            } catch (e: Exception) {
                logger.error("Error installing Lambda client", e)
            }
        }
    }

    fun downloadLatestLoader(): ByteArray? {
        return try {
            val branch = loaderBranch
            val mcVersion = getMinecraftVersion()

            if (debug) logger.info("Downloading loader for MC $mcVersion from ${branch.name} branch")

            var version: String?
            var baseUrl: String?

            when (branch) {
                Branch.Stable -> {
                    val xml = URI(LOADER_RELEASES_META).toURL().readText()
                    version = parseLatestVersion(xml, null)
                    baseUrl = "$MAVEN_URL/releases"
                }
                Branch.Snapshot -> {
                    val xml = URI(LOADER_SNAPSHOTS_META).toURL().readText()
                    version = parseLatestVersion(xml, null)
                    baseUrl = "$MAVEN_URL/snapshots"
                }
            }

            if (version == null && branch == Branch.Stable) {
                logger.warn("No stable loader found, falling back to snapshot")
                val xml = URI(LOADER_SNAPSHOTS_META).toURL().readText()
                version = parseLatestVersion(xml, null)
                baseUrl = "$MAVEN_URL/snapshots"
            }

            if (version == null) {
                logger.error("No loader version found")
                return null
            }

            val jarUrl = if (version.endsWith("-SNAPSHOT")) {
                val snapshotInfo = getSnapshotInfo(baseUrl, "com/lambda/loader", version) ?: return null
                val baseVersion = version.replace("-SNAPSHOT", "")
                "$baseUrl/com/lambda/loader/$version/loader-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            } else {
                "$baseUrl/com/lambda/loader/$version/loader-$version.jar"
            }

            if (debug) logger.info("Downloading from: $jarUrl")

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.error("Failed to download loader", e)
            null
        }
    }

    fun downloadLatestClient(): ByteArray? {
        return try {
            val branch = clientBranch
            val mcVersion = getMinecraftVersion()

            if (debug) logger.info("Downloading client for MC $mcVersion from ${branch.name} branch")

            var version: String?
            var baseUrl: String?

            when (branch) {
                Branch.Stable -> {
                    val xml = URI(CLIENT_RELEASES_META).toURL().readText()
                    version = parseLatestVersion(xml, mcVersion)
                    baseUrl = "$MAVEN_URL/releases"
                }
                Branch.Snapshot -> {
                    val xml = URI(CLIENT_SNAPSHOTS_META).toURL().readText()
                    version = parseLatestVersion(xml, mcVersion)
                    baseUrl = "$MAVEN_URL/snapshots"
                }
            }

            if (version == null && branch == Branch.Stable) {
                logger.warn("No stable client found for MC $mcVersion, falling back to snapshot")
                val xml = URI(CLIENT_SNAPSHOTS_META).toURL().readText()
                version = parseLatestVersion(xml, mcVersion)
                baseUrl = "$MAVEN_URL/snapshots"
            }

            if (version == null) {
                logger.error("No client version found for MC $mcVersion")
                return null
            }

            val jarUrl = if (version.endsWith("-SNAPSHOT")) {
                val snapshotInfo = getSnapshotInfo(baseUrl, "com/lambda/lambda", version) ?: return null
                val baseVersion = version.replace("-SNAPSHOT", "")
                "$baseUrl/com/lambda/lambda/$version/lambda-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            } else "$baseUrl/com/lambda/lambda/$version/lambda-$version.jar"

            if (debug) logger.info("Downloading from: $jarUrl")

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.error("Failed to download client", e)
            null
        }
    }

    private fun parseLatestVersion(xml: String, mcVersion: String? = null): String? {
        return try {
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
                    logger.info("Target MC version: $mcVersion")
                }
                logger.info("Available versions: ${versions.joinToString(", ")}")
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
                    logger.warn("No versions found $versionMsg")
                }
                null
            } else matchingVersions.last()
        } catch (e: Exception) {
            logger.error("Error parsing version", e)
            null
        }
    }

    private fun getSnapshotInfo(baseUrl: String, artifactPath: String, version: String): SnapshotInfo? {
        return try {
            val snapshotMetaUrl = URI("$baseUrl/$artifactPath/$version/maven-metadata.xml").toURL()
            val xml = snapshotMetaUrl.readText()

            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val timestamp = document.getElementsByTagName("timestamp").item(0).textContent
            val buildNumber = document.getElementsByTagName("buildNumber").item(0).textContent

            SnapshotInfo(version, timestamp, buildNumber)
        } catch (e: Exception) {
            logger.error("Error getting snapshot info", e)
            null
        }
    }

    private fun getMinecraftVersion() = SharedConstants.getGameVersion().name()

    private fun getModJarPath(modId: String): Path {
        val fabricLoader = FabricLoader.getInstance()
        val modContainer = fabricLoader.getModContainer(modId)
        return modContainer.get().origin.paths[0].toAbsolutePath()
    }

    private data class SnapshotInfo(
        val version: String,
        val timestamp: String,
        val buildNumber: String
    )
}