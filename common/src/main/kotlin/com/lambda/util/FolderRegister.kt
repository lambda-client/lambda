package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.util.FolderRegister.config
import com.lambda.util.FolderRegister.lambda
import com.lambda.util.FolderRegister.minecraft
import com.lambda.util.StringUtils.sanitizeForFilename
import java.io.File
import java.net.InetSocketAddress

/**
 * The [FolderRegister] object is responsible for managing the directory structure of the application.
 *
 * @property minecraft The root directory of the Minecraft client. It is retrieved using the [Platform.getGameFolder] function of the Architectury API.
 * @property lambda The directory for the Lambda client, located within the Minecraft directory.
 * @property config The directory for storing configuration files, located within the Lambda directory.
 */
object FolderRegister {
    val minecraft: File = mc.runDirectory
    val lambda: File = File(minecraft, "lambda")
    val config: File = File(lambda, "config")
    val packetLogs: File = File(lambda, "packet-log")
    val replay: File = File(lambda, "replay")

    fun File.createIfNotExists() {
        if (!exists()) { mkdirs() }
    }

    fun File.listRecursive() = walk().filter { it.isFile }

    fun SafeContext.locationBoundDirectory(file: File): File {
        val hostName = (connection.connection.address as? InetSocketAddress)?.hostName ?: "singleplayer"
        val path = file.resolve(
            hostName.sanitizeForFilename()
        ).resolve(
            world.dimensionKey?.value?.path?.sanitizeForFilename() ?: "unknown"
        )
        path.createIfNotExists()
        return path
    }
}
