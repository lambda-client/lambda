package com.lambda.client.util.filesystem

import java.awt.Desktop
import java.io.File
import java.net.URL

object FolderUtils {
    @JvmStatic
    val versionsFolder
        get() = "${minecraftFolder}versions${File.separator}"

    @JvmStatic
    val modsFolder
        get() = "${minecraftFolder}mods${File.separator}"

    /**
     * The Minecraft folder specific to the current operating system
     */
    private val minecraftFolder: String
        get() = when (getOS()) {
            OperatingSystem.UNIX -> System.getProperty("user.home") + "/.minecraft/"
            OperatingSystem.OSX -> System.getProperty("user.home") + "/Library/Application Support/minecraft/"
            OperatingSystem.WINDOWS -> System.getenv("APPDATA") + File.separator + ".minecraft" + File.separator
        }

    /**
     * Opens the given folder using the right library based on OS
     */
    fun openFolder(path: String) {
        Thread {
            if (getOS() == OperatingSystem.WINDOWS) Desktop.getDesktop().open(File(path))
            else Runtime.getRuntime().exec(getURLOpenCommand(File(path).toURI().toURL()))
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

    private enum class OperatingSystem {
        UNIX, OSX, WINDOWS
    }
}