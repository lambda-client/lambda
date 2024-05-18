package com.lambda.plugin

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.plugin.api.Plugin
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.mods
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import java.io.File
import java.lang.reflect.Method
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

object PluginRegistry : Loadable {
    val plugins = mutableListOf<Plugin>()

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
                    loadingError.format(
                        pluginClass,
                        "The plugin does not have an object instance or a public constructor"
                    ).also { LOG.warn(it) }
                }

            plugins.add(instance ?: return@forEach)
        }

        // TODO: Implement API logic here

        return "Registered ${plugins.size} plugins"
    }
}
