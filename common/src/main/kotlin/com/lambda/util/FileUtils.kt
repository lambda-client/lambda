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

import com.lambda.Lambda.mc
import com.lambda.network.LambdaHttp
import com.lambda.network.download
import com.lambda.util.StringUtils.sanitizeForFilename
import io.ktor.client.request.*
import java.io.File
import java.net.InetSocketAddress
import kotlin.math.sign
import kotlin.time.Duration

object FileUtils {
    /**
     * Returns a sequence of all the files in a tree that matches the [predicate]
     */
    fun File.listRecursive(predicate: (File) -> Boolean): Sequence<File> = walk().filter(predicate)

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
     * Executes the [block] if the file is older than the given [duration]
     */
    inline fun File.isOlderThan(duration: Duration, block: (File) -> Unit) =
        ifExists { if (duration.inWholeMilliseconds < System.currentTimeMillis() - lastModified()) block(this) }

    /**
     * Returns whether the receiver file is older than [duration]
     */
    fun File.isOlderThan(duration: Duration) =
        duration.inWholeMilliseconds < System.currentTimeMillis() - lastModified()

    /**
     * Executes the [block] if the receiver file exists and is not empty
     */
    inline fun File.ifExists(block: (File) -> Unit): File {
        if (length() > 0) block(this)
        return this
    }

    /**
     * Ensures the current file exists by creating it if it does not.
     *
     * If the file already exists, it will not be recreated. The necessary
     * parent directories will be created if they do not exist.
     *
     * @param block Lambda executed if the file doesn't exist or the file is empty
     */
    inline fun File.createIfNotExists(block: (File) -> Unit = {}): File {
        if (length() == 0L) {
            parentFile.mkdirs()
            createNewFile()

            block(this)
        }

        return this
    }

    /**
     * Executes the [block] if the receiver file does not exist or is empty.
     */
    inline fun File.ifNotExists(block: (File) -> Unit): File {
        if (length() == 0L) block(this)
        return this
    }

    /**
     * Modifies the receiver file if the downloaded file compare check succeeds
     *
     * @receiver The destination file to write the bytes to
     *
     * @param url The url to download the file from
     * @param compare Compare method. -1 if remote is larger. 0 if both file have the same size. 1 if local is larger
     * @param block Configuration block for the request
     *
     * @return An exception or the file
     */
    suspend fun File.downloadCompare(
        url: String,
        compare: Int,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching {
        createIfNotExists {
            val bytes = readBytes()
            val remote = LambdaHttp.download(url, block)
            val sign = (bytes.size - remote.size).sign

            if (sign == compare) writeBytes(remote)
        }
    }

    /**
     * Downloads the given file url if the file is not present
     *
     * @receiver The destination file to write the bytes to
     *
     * @param url The url to download the file from
     * @param block Configuration block for the request
     *
     * @return An exception or the file
     */
    suspend fun File.downloadIfNotPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { createIfNotExists { LambdaHttp.download(url, this, block) } }

    /**
     * Downloads the given file url if the file is not present
     *
     * @receiver The url to download the file from
     *
     * @param file The destination file to write the bytes to
     * @param block Configuration block for the request
     *
     * @return An exception or the file
     */
    suspend fun String.downloadIfNotPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.createIfNotExists { LambdaHttp.download(this, file, block) } }

    /**
     * Lambda that downloads the given file url if the file is not present
     *
     * @receiver The destination file to write the bytes to
     * @param block Configuration block for the request
     *
     * @return A lambda that returns an exception or the file
     */
    fun File.downloadIfNotPresent(block: HttpRequestBuilder.() -> Unit = {}): suspend (String) -> Result<Unit> =
        { url -> runCatching { createIfNotExists { LambdaHttp.download(url, this, block) } } }

    /**
     * Downloads the given file url if the file is present
     *
     * @receiver The destination file to write the bytes to
     *
     * @param url The url to download the file from
     * @param block Configuration block for the request
     *
     * @return An exception or the file
     */
    suspend fun File.downloadIfPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { ifExists { LambdaHttp.download(url, this, block) } }

    /**
     * Downloads the given file url if the file is present
     *
     * @receiver The url to download the file from
     *
     * @param file The destination file to write the bytes to
     * @param block Configuration block for the request
     *
     * @return An exception or the file
     */
    suspend fun String.downloadIfPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.ifExists { LambdaHttp.download(this, file, block) } }
}
