package org.kamiblue.client.plugin

import org.kamiblue.client.LambdaMod
import org.kamiblue.client.gui.mc.KamiGuiPluginError
import org.kamiblue.client.util.Wrapper

internal enum class PluginError {
    HOT_RELOAD,
    DUPLICATE,
    UNSUPPORTED,
    REQUIRED_PLUGIN;

    fun handleError(loader: PluginLoader) {
        val list = latestErrors ?: ArrayList<Pair<PluginLoader, PluginError>>().also { latestErrors = it }

        when (this) {
            HOT_RELOAD -> {
                LambdaMod.LOG.error("Plugin $loader cannot be hot reloaded.")
            }
            DUPLICATE -> {
                LambdaMod.LOG.error("Duplicate plugin ${loader}.")
            }
            UNSUPPORTED -> {
                LambdaMod.LOG.error("Unsupported plugin ${loader}. Required KAMI Blue version: ${loader.info.minApiVersion}")
            }
            REQUIRED_PLUGIN -> {
                LambdaMod.LOG.error("Missing required plugin for ${loader}. Required plugins: ${loader.info.requiredPlugins.joinToString()}")
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
                Wrapper.minecraft.displayGuiScreen(KamiGuiPluginError(Wrapper.minecraft.currentScreen, errors))
            }
        }
    }

}