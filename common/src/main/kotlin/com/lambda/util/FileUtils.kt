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

package com.lambda.util

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.httpDownload
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.getOrNull
import com.lambda.Lambda.mc
import com.lambda.util.StringUtils.sanitizeForFilename
import java.io.File
import java.net.InetSocketAddress

object FileUtils {
    /**
     * Returns a sequence of all the files in a tree that matches the [predicate]
     */
    fun File.listRecursive(predicate: (File) -> Boolean): Sequence<File> = walk().filter(predicate)

    /**
     * Ensures the current file exists by creating it if it does not.
     *
     * If the file already exists, it will not be recreated. The necessary
     * parent directories will be created if they do not exist.
     */
    fun File.createIfNotExists() = also { parentFile.mkdirs(); createNewFile() }

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
            mc.world?.dimensionKey?.value?.path?.sanitizeForFilename() ?: "unknown" // TODO: Change with utils when merged to master
        )
        path.mkdirs()
        return path
    }

    /**
     * Executes the [block] if the receiver file exists
     */
    inline fun File.ifExists(block: (File) -> Unit): File {
        if (exists()) block(this)
        return this
    }

    /**
     * Executes the [block] if the receiver file does not exist.
     */
    inline fun File.ifNotExists(block: (File) -> Unit): File {
        if (!exists()) block(this)
        return this
    }

    /**
     * Downloads the given file url if the file is not present
     *
     * This function does not guarantee that the given file will be created
     */
    fun File.downloadIfNotPresent(
        url: String,
        success: (ByteArray) -> Unit = {},
        failure: (FuelError) -> Unit = {}
    ) = ifNotExists { url.httpDownload().fileDestination { _, _ -> it }.response { _, _, result -> result.fold(success, failure) } }

    /**
     * Downloads the given file url if the file is not present
     *
     * This function does not guarantee that the given file will be created
     */
    fun String.downloadIfNotPresent(
        file: File,
        success: (ByteArray) -> Unit = {},
        failure: (FuelError) -> Unit = {}
    ) = file.ifNotExists { httpDownload().fileDestination { _, _ -> it }.response { _, _, result -> result.fold(success, failure) } }

    /**
     * Downloads the given file url if the file is not present
     *
     * This function does not guarantee that the given file will be created
     */
    fun File.downloadIfNotPresent(): (String) -> Unit =
        { url -> ifNotExists { url.httpDownload().fileDestination { _, _ -> it }.response { _, _, _ -> } } }

    /**
     * Gets the given url if the file is not present
     *
     * This function does not guarantee that the given file will be created
     */
    fun File.getIfNotPresent(): (String) -> Unit =
        { url -> ifNotExists { url.httpGet().responseString().third.getOrNull()?.let { writeText(it) } } }
}
