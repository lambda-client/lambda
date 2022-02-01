package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
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

            // Because main thread comedy
            Thread { Runtime.getRuntime().exec(getURLOpenCommand(path.toURI().toURL())) }.start()
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