package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.gui.mc.LambdaGuiPluginError
import com.lambda.client.util.Wrapper

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    UNSUPPORTED,
    REQUIRED_PLUGIN;

    fun handleError(loader: IPluginLoader) {
        val list = latestErrors ?: ArrayList<Pair<IPluginLoader, PluginError>>().also { latestErrors = it }

        when (this) {
            HOT_RELOAD -> {
                LambdaMod.LOG.error("Plugin $loader cannot be hot reloaded.")
            }
            DUPLICATE -> {
                LambdaMod.LOG.error("Duplicate plugin ${loader}.")
            }
            UNSUPPORTED -> {
                LambdaMod.LOG.error("Unsupported plugin ${loader}. Required Lambda version: ${loader.info.minApiVersion}")
            }
            REQUIRED_PLUGIN -> {
                LambdaMod.LOG.error("Missing required plugin for ${loader}. Required plugins: ${loader.info.requiredPlugins.joinToString()}")
            }
        }

        list.add(loader to this)
    }

    companion object {
        private var latestErrors: ArrayList<Pair<IPluginLoader, PluginError>>? = null

        fun displayErrors() {
            val errors = latestErrors
            latestErrors = null

            if (!errors.isNullOrEmpty()) {
                Wrapper.minecraft.displayGuiScreen(LambdaGuiPluginError(Wrapper.minecraft.currentScreen, errors))
            }
        }
    }

}