package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.util.FolderRegister.config
import com.lambda.util.FolderRegister.lambda
import com.lambda.util.FolderRegister.minecraft
import com.lambda.util.FolderRegister.mods
import com.lambda.util.FolderRegister.packetLogs
import com.lambda.util.FolderRegister.replay
import com.lambda.util.StringUtils.sanitizeForFilename
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.MessageDigest

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

    fun File.createIfNotExists() {
        createFileIfNotExists(this.name, this.parentFile)
    }

    fun File.listRecursive(predicate: (File) -> Boolean = { true }) = walk().filter(predicate)

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

    /**
     * Returns a file with the given name in the specified directory, creating it if it does not exist.
     * If the directory is not specified, it will try to parse it from the name.
     * Otherwise, it will default to the Lambda directory.
     *
     * @param name The name of the file.
     * @param directory The directory in which the file is located. Default is the Lambda directory.
     * @param hash Whether to hash the name of the file.
     * @return A pair containing the file and a boolean indicating whether the file was created.
     */
    fun createFileIfNotExists(name: String, directory: File? = null, hash: Boolean = false): Pair<File, Boolean> {
        var parsedDir: File = directory ?: lambda

        if (directory == null) {
            parsedDir = name.substringAfterLast('/').substringBeforeLast('.')
                .let { if (it.isEmpty()) lambda else File(it) }
        }

        val compiledName =
            if (hash) DigestUtils.sha256Hex(name)
            else name

        val file = File(parsedDir, compiledName)
        val created = !file.exists()

        if (created) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        return file to created
    }


    /**
     * Returns a file with the given name in the specified directory, creating it if it does not exist.
     * If the directory is not specified, it will try to parse it from the name.
     * Otherwise, it will default to the Lambda directory.
     *
     * @param name The name of the file.
     * @param directory The directory in which the file is located. Default is the Lambda directory.
     * @param compute A lambda function to compute the file contents if it was created.
     */
    @JvmName("getFileOrComputeByteArray")
    inline fun getFileOrCompute(name: String, directory: File? = null, compute: () -> ByteArray): File {
        val (file, wasCreated) = createFileIfNotExists(name, directory)
        if (wasCreated) file.outputStream().use { it.write(compute()) }

        return file
    }

    /**
     * Returns a file with the given name in the specified directory, creating it if it does not exist.
     * If the directory is not specified, it will try to parse it from the name.
     * Otherwise, it will default to the Lambda directory.
     *
     * @param name The name of the file.
     * @param directory The directory in which the file is located. Default is the Lambda directory.
     * @param compute A lambda function to compute the file contents if it was created.
     */
    @JvmName("getFileOrComputeInputStream")
    inline fun getFileOrCompute(name: String, directory: File? = null, compute: () -> InputStream): File {
        val (file, wasCreated) = createFileIfNotExists(name, directory)
        if (wasCreated) file.outputStream().use { compute().copyTo(it) }

        return file
    }
}
