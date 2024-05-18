package com.lambda.plugin

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.plugin.api.Plugin
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.mods
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

object PluginRegistry : Loadable {
    val plugins = mutableMapOf<String, Plugin>()

    private val loadingError = """
                An error occurred while loading a plugin.
                If you are a developer, please check the plugin's main class, instance or load method.
                If you are a regular user, please report this issue to the plugin developer.
                
                Plugin: %s
                Error: %s
                """.trimIndent()

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .addUrls(
                    mods.listRecursive { it.extension == "jar" }
                        .map { it.toURI().toURL() }
                        .toList()
                )
                .addScanners(Scanners.SubTypes)
        ).getSubTypesOf(Plugin::class.java).forEach { pluginClass ->
            val instance = (pluginClass.declaredFields.find { it.name == "INSTANCE" }?.get(null)
                ?: pluginClass.constructors.firstOrNull()?.newInstance()) as? Plugin
                ?: null.also {
                    LOG.warn(
                        loadingError.format(
                            pluginClass,
                            "The plugin does not have an object instance or a public constructor"
                        )
                    )
                }

            val plugin = instance ?: return@forEach
            plugins[plugin.name] = plugin
        }

        //plugins.forEach { (_, plugin) ->
//            node(plugin, plugin
//                .dependencies
//                ?.mapNotNull { name ->
//                    plugins[name] ?: return@mapNotNull null.also {
//                        LOG.error("Plugin ${plugin.name} has a dependency on $name, which does not exist")
//                    }
//                } ?: emptyList())

        return "Loaded ${plugins.size} plugins"
    }
}
