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

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread

object AutoUpdater : Module(
    name = "AutoUpdater",
    description = "Installs / uninstalls Lambda loader",
    tag = ModuleTag.CLIENT,
) {
    private val logger = LoggerFactory.getLogger("AutoUpdater")

    private val debug by setting("Debug", false, "Enable debug logging")
    private val loaderBranch by setting("Loader Branch", Branch.STABLE, "Select loader update branch")
    private val clientBranch by setting("Client Branch", Branch.STABLE, "Select client update branch")

    private enum class Branch {
        STABLE,
        SNAPSHOT
    }

    init {
        onEnable {
            //TODO: Add modal to prompt user for confirmation before updating and restart after download and confirmation
            thread(name = "Lambda-Client-Updater") {
                try {
                    // Check if Lambda client mod is present
                    if (!isModContainerPresent("lambda")) {
                        logger.error("Lambda client mod not found!")
                        return@thread
                    }

                    logger.info("Starting Lambda client update...")

                    // Download the latest client JAR
                    val clientJar = downloadLatestClient()
                    if (clientJar == null) {
                        logger.error("Failed to download latest Lambda client")
                        return@thread
                    }

                    // Get the current Lambda mod JAR path
                    val lambdaJarPath = getModJarPath("lambda")
                    if (debug) {
                        logger.info("Lambda JAR path: $lambdaJarPath")
                    }

                    // Write the new JAR to replace the old one
                    val jarFile = lambdaJarPath.toFile()
                    jarFile.writeBytes(clientJar)

                    logger.info("Successfully updated Lambda client! Restart required.")
                } catch (e: Exception) {
                    logger.error("Error updating Lambda client", e)
                }
            }
        }

        onDisable {
            //TODO: Add modal to prompt user for confirmation before updating and restart after download and confirmation
            thread(name = "Lambda-Loader-Updater") {
                try {
                    // Check if Lambda loader mod is present
                    if (!isModContainerPresent("lambda-loader")) {
                        logger.error("Lambda loader mod not found!")
                        return@thread
                    }

                    logger.info("Starting Lambda loader update...")

                    // Download the latest loader JAR
                    val loaderJar = downloadLatestLoader()
                    if (loaderJar == null) {
                        logger.error("Failed to download latest Lambda loader")
                        return@thread
                    }

                    // Get the current Lambda loader mod JAR path
                    val loaderJarPath = getModJarPath("lambda-loader")
                    if (debug) {
                        logger.info("Lambda loader JAR path: $loaderJarPath")
                    }

                    // Write the new JAR to replace the old one
                    val jarFile = loaderJarPath.toFile()
                    jarFile.writeBytes(loaderJar)

                    logger.info("Successfully updated Lambda loader! Restart required.")
                } catch (e: Exception) {
                    logger.error("Error updating Lambda loader", e)
                }
            }
        }
    }

    // Maven URLs
    private const val MAVEN_URL = "https://maven.lambda-client.org"
    private const val MAVEN_THC_URL = "https://maven.lambda-client.org"
    private const val LOADER_RELEASES_META = "$MAVEN_THC_URL/releases/com/lambda/loader/maven-metadata.xml"
    private const val LOADER_SNAPSHOTS_META = "$MAVEN_THC_URL/snapshots/com/lambda/loader/maven-metadata.xml"
    private const val CLIENT_RELEASES_META = "$MAVEN_URL/releases/com/lambda/lambda/maven-metadata.xml"
    private const val CLIENT_SNAPSHOTS_META = "$MAVEN_URL/snapshots/com/lambda/lambda/maven-metadata.xml"

    /**
     * Get the current Minecraft version
     */
    private fun getMinecraftVersion(): String {
        return SharedConstants.getGameVersion().name()
    }

    private fun isModContainerPresent(modId: String): Boolean {
        val fabricLoader = FabricLoader.getInstance()
        return fabricLoader.getModContainer(modId).isPresent
    }

    private fun getModJarPath(modId: String): Path {
        val fabricLoader = FabricLoader.getInstance()
        val modContainer = fabricLoader.getModContainer(modId)
        return modContainer.get().origin.paths[0].toAbsolutePath()
    }

    /**
     * Parse latest version from maven-metadata.xml, optionally filtering by MC version
     */
    private fun parseLatestVersion(xml: String, mcVersion: String? = null): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            for (i in 0 until versionNodes.length) {
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
            } else {
                matchingVersions.last()
            }
        } catch (e: Exception) {
            logger.error("Error parsing version", e)
            null
        }
    }

    /**
     * Get snapshot info (timestamp and build number)
     */
    private data class SnapshotInfo(
        val version: String,
        val timestamp: String,
        val buildNumber: String
    )

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

    /**
     * Download the latest loader JAR from selected branch or fallback to snapshot
     */
    fun downloadLatestLoader(): ByteArray? {
        return try {
            val branch = loaderBranch
            val mcVersion = getMinecraftVersion()

            if (debug) {
                logger.info("Downloading loader for MC $mcVersion from ${branch.name} branch")
            }

            // Try selected branch first
            var version: String?
            var baseUrl: String?

	        when (branch) {
                Branch.STABLE -> {
                    val xml = URI(LOADER_RELEASES_META).toURL().readText()
                    version = parseLatestVersion(xml, null)
                    baseUrl = "$MAVEN_URL/releases"
                }
                Branch.SNAPSHOT -> {
                    val xml = URI(LOADER_SNAPSHOTS_META).toURL().readText()
                    version = parseLatestVersion(xml, null)
                    baseUrl = "$MAVEN_URL/snapshots"
                }
            }

            // Fallback to snapshot if stable not found
            if (version == null && branch == Branch.STABLE) {
                logger.warn("No stable loader found, falling back to snapshot")
                val xml = URI(LOADER_SNAPSHOTS_META).toURL().readText()
                version = parseLatestVersion(xml, null)
                baseUrl = "$MAVEN_URL/snapshots"
            }

            if (version == null) {
                logger.error("No loader version found")
                return null
            }

            // Build download URL
            val jarUrl = if (version.endsWith("-SNAPSHOT")) {
                val snapshotInfo = getSnapshotInfo(baseUrl, "com/lambda/loader", version) ?: return null
                val baseVersion = version.replace("-SNAPSHOT", "")
                "$baseUrl/com/lambda/loader/$version/loader-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            } else {
                "$baseUrl/com/lambda/loader/$version/loader-$version.jar"
            }

            if (debug) {
                logger.info("Downloading from: $jarUrl")
            }

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.error("Failed to download loader", e)
            null
        }
    }

    /**
     * Download the latest client JAR for current MC version from selected branch or fallback to snapshot
     */
    fun downloadLatestClient(): ByteArray? {
        return try {
            val branch = clientBranch
            val mcVersion = getMinecraftVersion()

            if (debug) {
                logger.info("Downloading client for MC $mcVersion from ${branch.name} branch")
            }

            // Try selected branch first
            var version: String?
            var baseUrl: String?

            when (branch) {
                Branch.STABLE -> {
                    val xml = URI(CLIENT_RELEASES_META).toURL().readText()
                    version = parseLatestVersion(xml, mcVersion)
                    baseUrl = "$MAVEN_URL/releases"
                }
                Branch.SNAPSHOT -> {
                    val xml = URI(CLIENT_SNAPSHOTS_META).toURL().readText()
                    version = parseLatestVersion(xml, mcVersion)
                    baseUrl = "$MAVEN_URL/snapshots"
                }
            }

            // Fallback to snapshot if stable not found
            if (version == null && branch == Branch.STABLE) {
                logger.warn("No stable client found for MC $mcVersion, falling back to snapshot")
                val xml = URI(CLIENT_SNAPSHOTS_META).toURL().readText()
                version = parseLatestVersion(xml, mcVersion)
                baseUrl = "$MAVEN_URL/snapshots"
            }

            if (version == null) {
                logger.error("No client version found for MC $mcVersion")
                return null
            }

            // Build download URL
            val jarUrl = if (version.endsWith("-SNAPSHOT")) {
                val snapshotInfo = getSnapshotInfo(baseUrl, "com/lambda/lambda", version) ?: return null
                val baseVersion = version.replace("-SNAPSHOT", "")
                "$baseUrl/com/lambda/lambda/$version/lambda-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            } else {
                "$baseUrl/com/lambda/lambda/$version/lambda-$version.jar"
            }

            if (debug) {
                logger.info("Downloading from: $jarUrl")
            }

            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.error("Failed to download client", e)
            null
        }
    }
}