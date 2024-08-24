package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.util.FolderRegister.config
import com.lambda.util.FolderRegister.lambda
import com.lambda.util.FolderRegister.minecraft
import com.lambda.util.FolderRegister.mods
import com.lambda.util.FolderRegister.packetLogs
import com.lambda.util.FolderRegister.replay
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

    fun File.createIfNotExists() {
        if (!exists()) {
            mkdirs()
        }
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
}
