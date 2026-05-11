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

package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.network.LambdaHttp
import com.lambda.network.download
import com.lambda.util.StringUtils.sanitizeForFilename
import com.lambda.util.extension.dimensionName
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
        ).resolve(mc.world.dimensionName)

        path.mkdirs()
        return path
    }

    inline fun File.isOlderThan(duration: Duration, block: (File) -> Unit) =
        ifExists { if (duration.inWholeMilliseconds < System.currentTimeMillis() - lastModified()) block(this) }

    fun File.isOlderThan(duration: Duration) =
        duration.inWholeMilliseconds < System.currentTimeMillis() - lastModified()

    inline fun File.ifExists(block: (File) -> Unit): File {
        if (length() > 0) block(this)
        return this
    }

    inline fun File.createIfNotExists(block: (File) -> Unit = {}): File {
        if (length() == 0L) {
            parentFile.mkdirs()
            createNewFile()

            block(this)
        }

        return this
    }

    inline fun File.ifNotExists(block: (File) -> Unit): File {
        if (length() == 0L) block(this)
        return this
    }

    /**
     * Changes the local file if [compare]:
     * - is -1 and the remote is larger
     * - is 1 and local is larger
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

    suspend fun File.downloadIfNotPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { createIfNotExists { LambdaHttp.download(url, this, block) } }

    suspend fun String.downloadIfNotPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.createIfNotExists { LambdaHttp.download(this, file, block) } }

    suspend fun File.downloadIfPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { ifExists { LambdaHttp.download(url, this, block) } }

    suspend fun String.downloadIfPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.ifExists { LambdaHttp.download(this, file, block) } }
}
