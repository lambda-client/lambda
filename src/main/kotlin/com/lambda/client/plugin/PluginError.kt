package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.gui.mc.LambdaGuiPluginError
import com.lambda.client.util.Wrapper
import com.lambda.client.util.text.MessageSendHelper

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    UNSUPPORTED,
    REQUIRED_PLUGIN;

    fun handleError(loader: PluginLoader) {
        val list = latestErrors ?: ArrayList<Pair<PluginLoader, PluginError>>().also { latestErrors = it }

        when (this) {
            HOT_RELOAD -> {
                log("Plugin $loader cannot be hot reloaded.")
            }
            DUPLICATE -> {
                log("Duplicate plugin ${loader}.")
            }
            UNSUPPORTED -> {
                log("Unsupported plugin ${loader}. Required Lambda version: ${loader.info.minApiVersion}")
            }
            REQUIRED_PLUGIN -> {
                log("Missing required plugin for ${loader}. Required plugins: ${loader.info.requiredPlugins.joinToString()}")
            }
        }

        list.add(loader to this)
    }

    companion object {
        private var latestErrors: ArrayList<Pair<PluginLoader, PluginError>>? = null

        fun displayErrors() {
            val errors = latestErrors
            latestErrors = null

            if (!errors.isNullOrEmpty()) {
                Wrapper.minecraft.displayGuiScreen(LambdaGuiPluginError(Wrapper.minecraft.currentScreen, errors))
            }
        }

        fun log(message: String?, throwable: Throwable? = null) {
            if (throwable != null) {
                LambdaMod.LOG.error(message, throwable)
            } else {
                LambdaMod.LOG.error(message)
            }
            if (message != null) {
                MessageSendHelper.sendChatMessage(message)
            }
        }
    }

}