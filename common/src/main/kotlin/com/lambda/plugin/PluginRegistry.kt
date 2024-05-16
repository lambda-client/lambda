package com.lambda.plugin

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.mods
import java.io.File
import java.util.jar.JarFile

object PluginRegistry : Loadable {
    private val classLoaderError = """
                An error occurred while retrieving the thread class loader.
                This likely mean that newer versions of the mod loader
                have changed the way mods are loaded, affecting the plugin system.
                Please report this error to the Lambda developers with the following information:
                - The version of Lambda
                - The version of the mod loader (ex. Fabric, Forge, etc.)
                - The version of Minecraft you are using
                - The version of Java you are using
                
                Plugin: %s
                Stacktrace:
                %s
                """.trimIndent()

    private val loadingError = """
                An error occurred while loading a plugin.
                If you are a developer, please check the plugin's main class and load method.
                If you are a regular user, please report this issue to the plugin developer.
                
                Plugin: %s
                Error: %s
                Stacktrace:
                %s
                """.trimIndent()

    private fun loadPlugin(file: File) {
        runCatching {
            val jar = JarFile(file)

            val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
                ?: return LOG.error("The plugin $jar does not have a main class")

            val loader = PluginLoader(jar,
                Thread.currentThread().contextClassLoader ?:
                        return classLoaderError.format(
                            file,
                            Thread.currentThread().stackTrace.joinToString("\n")
                        )
                            .split("\n")
                            .forEach(LOG::error))

            val loadClass = loader.loadClass(mainClass)

            val loadInstance =
                loadClass.declaredFields.firstOrNull { it.name == "INSTANCE" }?.get(null)
                    ?: loadClass.constructors.firstOrNull()?.newInstance()
                        ?: return LOG.error("The plugin $jar does not have an object instance or a public constructor")
            val loadMethod =
                loadClass?.methods?.find { it.name == "load" }
                    ?: return LOG.warn("The plugin $jar does not have a load method")

            loadMethod.invoke(loadInstance)
        }.onFailure {
            val threadDump = Thread.getAllStackTraces().entries.joinToString("\n") {
                it.key.toString() + it.value.joinToString("\n") { "\tat $it" }
            }

            loadingError.format(
                file,
                it.message,
                threadDump
            )
                .split("\n")
                .forEach(LOG::error)
        }
    }

    override fun load(): String {
        val plugins = mods
            .listRecursive {
                it.isFile && it.extension == "jar"
                        // Not a fan of creating a new JarFile instance every iteration
                        && JarFile(it).manifest.mainAttributes.getValue("Lambda-Plugin") == "true"
            }
        val size = plugins.count()

        plugins.forEach(::loadPlugin)

        val plural = if (size == 1) "" else "s"

        return "Loaded $size plugin$plural"
    }
}
