package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.util.StringUtils.sanitizeForFilename
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

/**
 * The [FolderRegister] object is responsible for managing the directory structure of the application.
 *
 * @property minecraft The root directory of the Minecraft client.
 * @property lambda The directory for the Lambda client, located within the Minecraft directory.
 * @property config The directory for storing configuration files, located within the Lambda directory.
 * @property packetLogs The directory for storing packet logs, located within the Lambda directory.
 * @property replay The directory for storing replay files, located within the Lambda directory.
 */
object FolderRegister : Loadable {
    val minecraft: Path = mc.runDirectory.toPath()
    val lambda: Path = minecraft.resolve("lambda")
    val config: Path = lambda.resolve("config")
    val packetLogs: Path = lambda.resolve("packet-log")
    val replay: Path = lambda.resolve("replay")
    val cache: Path = lambda.resolve("cache")
    val structure: Path = lambda.resolve("structure")

    override fun load(): String {
        val folders = listOf(lambda, config, packetLogs, replay, cache, structure)
        val createdFolders = folders.mapNotNull {
            if (it.notExists()) {
                it.createDirectory()
                it
            } else null
        }
        return if (createdFolders.isNotEmpty()) {
            "\nCreated directories: ${createdFolders.joinToString { it.toString() }}"
        } else ""
    }

    /**
     * Ensures the current file exists by creating it if it does not.
     *
     * If the file already exists, it will not be recreated. The necessary
     * parent directories will be created if they do not exist.
     */
    fun File.createIfNotExists(): File = also { parentFile.mkdirs(); createNewFile() }

    /**
     * Returns a sequence of all the files in a tree that matches the [predicate]
     */
    fun File.listRecursive(predicate: (File) -> Boolean) = walk().filter(predicate)

    /**
     * Retrieves or creates a directory based on the current network connection and world dimension.
     *
     * The directory is determined by the host name of the current network connection (or "singleplayer" if offline)
     * and the dimension key of the current world. These values are sanitized for use as filenames and combined
     * to form a path under the current file. If the directory does not exist, it will be created.
     *
     * @receiver The base directory where the location-bound directory will be created.
     * @return A `File` object representing the location-bound directory.
     *
     * The path is structured as:
     * - `[base directory]/[host name]/[dimension key]`
     *
     * Example:
     * If playing on a server with hostname "example.com" and in the "overworld" dimension, the path would be:
     * - `[base directory]/example.com/overworld`
     */
    fun File.locationBoundDirectory(): File {
        val hostName = (mc.networkHandler?.connection?.address as? InetSocketAddress)?.hostName ?: "singleplayer"
        val path = resolve(
            hostName.sanitizeForFilename()
        ).resolve(
            mc.world?.dimensionKey?.value?.path?.sanitizeForFilename() ?: "unknown"
        )
        path.createIfNotExists()
        return path
    }
}
