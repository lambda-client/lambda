
package com.minato.util

import com.minato.Minato.mc
import com.minato.network.MINATO_HTTP
import com.minato.network.download
import com.minato.util.StringUtils.sanitizeForFilename
import com.minato.util.extension.dimensionName
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
            val remote = MINATO_HTTP.download(url, block)
            val sign = (bytes.size - remote.size).sign

            if (sign == compare) writeBytes(remote)
        }
    }

    suspend fun File.downloadIfNotPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { createIfNotExists { MINATO_HTTP.download(url, this, block) } }

    suspend fun String.downloadIfNotPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.createIfNotExists { MINATO_HTTP.download(this, file, block) } }

    suspend fun File.downloadIfPresent(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { ifExists { MINATO_HTTP.download(url, this, block) } }

    suspend fun String.downloadIfPresent(
        file: File,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = runCatching { file.ifExists { MINATO_HTTP.download(this, file, block) } }
}
