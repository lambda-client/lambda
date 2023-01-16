package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.manager.managers.NotificationManager
import java.io.File

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    DEPRECATED,
    UNSUPPORTED,
    REQUIRED_PLUGIN,
    OUTDATED_PLUGIN,
    CLASS_NOT_FOUND,
    ILLEGAL_ACCESS,
    MISSING_DEFINITION,
    OTHERS;

    fun handleError(loader: PluginLoader, message: String? = null, throwable: Throwable? = null) {
        when (this) {
            HOT_RELOAD -> {
                log("Plugin $loader cannot be hot reloaded.")
            }
            DUPLICATE -> {
                log("Duplicate plugin $loader.")
            }
            DEPRECATED -> {
                log("Plugin $loader is deprecated due to the presence of a newer version: $message")
            }
            UNSUPPORTED -> {
                log("Unsupported plugin $loader. Minimum required Lambda version: ${loader.info.minApiVersion}")
            }
            REQUIRED_PLUGIN -> {
                log("Missing required plugin for $loader. Required plugins: ${loader.info.requiredPlugins.joinToString()}")
            }
            OUTDATED_PLUGIN -> {
                log("The in $loader used Lambda API is outdated. Please update the plugin or notify the developer ${loader.info.authors.joinToString()}")
            }
            CLASS_NOT_FOUND -> {
                log("Main class not found", throwable)
            }
            ILLEGAL_ACCESS -> {
                log("Illegal access violation", throwable)
            }
            MISSING_DEFINITION -> {
                log("Missing definition. Please make sure compatible Lambda API is used.", throwable)
            }
            OTHERS -> {
                log(message, throwable)
            }
        }

        // append .disabled to the file name
        // if a file with the same name exists, append a number to the end
        var disabledFile = File("${loader.file.path}.disabled")
        var i = 1
        while (disabledFile.exists()) {
            disabledFile = File("${loader.file.path}.disabled$i")
            i++
        }
        loader.file.renameTo(disabledFile)
    }

    fun log(message: String?, throwable: Throwable? = null) {
        message?.let { NotificationManager.registerNotification("[Plugin Manager] Failed to load plugin. $it") }

        LambdaMod.LOG.error(message, throwable)
    }
}