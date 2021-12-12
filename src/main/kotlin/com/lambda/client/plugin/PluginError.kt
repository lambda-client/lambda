package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.util.text.MessageSendHelper

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    UNSUPPORTED,
    REQUIRED_PLUGIN;

    fun handleError(loader: PluginLoader) {
        val list = latestErrors ?: ArrayList<Pair<PluginLoader, PluginError>>().also { latestErrors = it }

        if (latestErrors?.none { it.first.file.name == loader.file.name && it.second == this } == true) {
            list.add(loader to this)

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
            }
        }
    }

    companion object {
        private var latestErrors: ArrayList<Pair<PluginLoader, PluginError>>? = null

        fun log(message: String?, throwable: Throwable? = null) {
            message?.let {
                MessageSendHelper.sendErrorMessage("[Plugin Manager] $it")

                if (throwable != null) {
                    LambdaMod.LOG.error(message, throwable)
                } else {
                    LambdaMod.LOG.error(message)
                }
            }
        }
    }

}