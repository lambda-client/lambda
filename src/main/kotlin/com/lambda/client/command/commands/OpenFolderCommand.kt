package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.filesystem.FolderUtils
import com.lambda.client.util.filesystem.FolderUtils.OperatingSystem
import java.awt.Desktop

import java.io.File
import java.net.URL

object OpenFolderCommand : ClientCommand(
    name = "openfolder",
    description = "Open your lambda client folder"
) {
    init {
        execute {
            val dotMCFile = File("")
            val path = File("${dotMCFile.absolutePath}/lambda")
            val os = FolderUtils.getOS()

            // Because main thread comedy
            Thread {
                if (os == OperatingSystem.WINDOWS) Desktop.getDesktop().open(path)
                else Runtime.getRuntime().exec(getURLOpenCommand(path.toURI().toURL()))
            }.start()
        }
    }

    private fun getURLOpenCommand(url: URL): Array<String> {
        var string: String = url.toString()
        if ("file" == url.protocol) {
            string = string.replace("file:", "file://")
        }
        return arrayOf("xdg-open", string)
    }
}