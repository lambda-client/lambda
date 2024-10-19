package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.util.StringUtils.sanitizeForFilename
import java.io.File
import java.net.InetSocketAddress

/**
 * The [FolderRegister] object is responsible for managing the directory structure of the application.
 *
 * @property minecraft The root directory of the Minecraft client.
 * @property lambda The directory for the Lambda client, located within the Minecraft directory.
 * @property mods The directory for storing mods, located within the Minecraft directory.
 * @property config The directory for storing configuration files, located within the Lambda directory.
 * @property packetLogs The directory for storing packet logs, located within the Lambda directory.
 * @property replay The directory for storing replay files, located within the Lambda directory.
 */
object FolderRegister {
    val minecraft: File = mc.runDirectory
    val lambda: File = File(minecraft, "lambda")
    val mods: File = File(minecraft, "mods")
    val config: File = File(lambda, "config")
    val packetLogs: File = File(lambda, "packet-log")
    val replay: File = File(lambda, "replay")
    val cache: File = File(lambda, "cache")
    val structure: File = File(lambda, "structure")

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
