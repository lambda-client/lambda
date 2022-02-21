package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.manager.managers.NotificationManager
import java.io.File

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    UNSUPPORTED,
    REQUIRED_PLUGIN,
    OTHERS;

    fun handleError(loader: PluginLoader, message: String? = null, throwable: Throwable? = null) {
        when (this) {
            HOT_RELOAD -> {
                log("Plugin $loader cannot be hot reloaded.")
            }
            DUPLICATE -> {
                log("Duplicate plugin ${loader}.")
            }
            UNSUPPORTED -> {
                log("Unsupported plugin ${loader}. Minimum required Lambda version: ${loader.info.minApiVersion}")
            }
            REQUIRED_PLUGIN -> {
                log("Missing required plugin for ${loader}. Required plugins: ${loader.info.requiredPlugins.joinToString()}")
            }
            OTHERS -> {
                log(message, throwable)
            }
        }

        loader.file.renameTo(File("${loader.file.path}.disabled"))
    }

    fun log(message: String?, throwable: Throwable? = null) {
        message?.let { NotificationManager.registerNotification("[Plugin Manager] Failed to load plugin: $it") }

        LambdaMod.LOG.error(message, throwable)
    }
}