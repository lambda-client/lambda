package com.lambda.client.plugin

import com.lambda.client.LambdaMod
import com.lambda.client.plugin.api.Plugin
import com.lambda.commons.utils.ClassUtils.instance
import java.io.FileNotFoundException

internal class DevPluginLoader : IPluginLoader {
    override val name: String get() = info.name
    override val info: PluginInfo = javaClass.classLoader.getResourceAsStream("plugin_info.json")?.let {
        PluginInfo.fromStream(it)
    } ?: throw FileNotFoundException("plugin_info.json not found in classpath!")

    init {
        // This will trigger the null checks in PluginInfo
        // In order to make sure all required infos are present
        LambdaMod.LOG.debug(info.toString())
    }

    override fun verify(): Boolean {
        return true
    }

    override fun load(): Plugin {
        if (LambdaMod.ready && !info.hotReload) {
            throw IllegalAccessException("Plugin $this cannot be hot reloaded!")
        }

        val clazz = Class.forName(info.mainClass, true, javaClass.classLoader)
        val obj = try {
            clazz.instance
        } catch (e: NoSuchFieldException) {
            clazz.newInstance()
        }

        val plugin = obj as? Plugin
            ?: throw IllegalArgumentException("The specific main class ${info.mainClass} is not a valid plugin main class")

        plugin.setInfo(info)
        return plugin
    }

    override fun close() {

    }

    override fun toString(): String {
        return runCatching { info.name }.getOrDefault("Unknown Plugin")
    }
}