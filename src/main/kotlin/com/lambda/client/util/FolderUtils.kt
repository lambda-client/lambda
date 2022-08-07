package com.lambda.client.util

import com.lambda.client.LambdaMod
import java.awt.Desktop
import java.io.File
import java.net.URL

object FolderUtils {
    @JvmStatic
    val minecraftFolder: String
        get() = "${File("").absolutePath}${File.separator}"

    @JvmStatic
    val versionsFolder
        get() = "${minecraftFolder}versions${File.separator}"

    @JvmStatic
    val modsFolder
        get() = "${minecraftFolder}mods${File.separator}"

    @JvmStatic
    val logFolder
        get() = "${minecraftFolder}logs${File.separator}"

    @JvmStatic
    val screenshotFolder
        get() = "${minecraftFolder}screenshots${File.separator}"

    @JvmStatic
    val lambdaFolder
        get() = "$minecraftFolder${LambdaMod.DIRECTORY}${File.separator}"

    @JvmStatic
    val pluginFolder
        get() = "${lambdaFolder}plugins${File.separator}"

    @JvmStatic
    val packetLogFolder
        get() = "${lambdaFolder}packet-logs${File.separator}"

    @JvmStatic
    val songFolder
        get() = "${lambdaFolder}songs${File.separator}"

    @JvmStatic
    val newChunksFolder
        get() = "${lambdaFolder}new-chunks${File.separator}"

    @JvmStatic
    val mapImagesFolder
        get() = "${lambdaFolder}map-images${File.separator}"

    /**
     * Opens the given path using the right library based on OS
     */
    fun openFolder(path: String) {
        Thread {
            val file = File(path)
            if (!file.exists()) file.mkdir()
            if (getOS() == OperatingSystem.WINDOWS) {
                Desktop.getDesktop().open(file)
            } else {
                Runtime.getRuntime().exec(getURLOpenCommand(file.toURI().toURL()))
            }
        }.start()
    }

    private fun getURLOpenCommand(url: URL): Array<String> {
        var string: String = url.toString()
        if ("file" == url.protocol) {
            string = string.replace("file:", "file://")
        }
        return arrayOf("xdg-open", string)
    }

    /**
     * @return current OperatingSystem
     */
    private fun getOS(): OperatingSystem {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("nux") -> {
                OperatingSystem.UNIX
            }
            osName.contains("darwin") || osName.contains("mac") -> {
                OperatingSystem.OSX
            }
            osName.contains("win") -> {
                OperatingSystem.WINDOWS
            }
            else -> {
                throw RuntimeException("Operating system couldn't be detected! Report this to the developers")
            }
        }
    }

    enum class OperatingSystem {
        UNIX, OSX, WINDOWS
    }
}