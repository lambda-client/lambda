package com.lambda.plugin

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.mods
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL
import java.util.jar.JarFile

object PluginRegistry : Loadable {
    private val errorMessageFMT = """
                A serious error occurred while loading a plugin.
                This is likely a bug in the plugin itself but it could also be a bug in Lambda.
                If you are a developer, please check the plugin's main class and load method.
                If you are a regular user, please report this issue to Lambda team and the plugin developer.
                
                Plugin: {}
                Stacktrace: {}
                """.trimIndent()

    private val threadClassLoader: ClassLoader = Thread.currentThread().contextClassLoader
    private val addUrlMethod: Method

    init {
        try {
            addUrlMethod =
                threadClassLoader.javaClass.getMethod("addUrlFwd", URL::class.java) // TODO: Check for other methods if not found

            addUrlMethod.isAccessible = true
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to get the addURL method from the KnotClassLoader.")
        }
    }

    @Throws(
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class)
    fun forceFeedJar(jar: File) {
        addUrlMethod.invoke(threadClassLoader, jar.toURI().toURL())
    }

    private fun loadPlugin(file: File) {
        runCatching {
            // Someone got a better idea?
            // Don't like nested try-catch blocks
            runCatching { forceFeedJar(file) }
                .onFailure {
                    LOG.error("Failed to feed the plugin {} to the thread class loader", file)
                }

            LOG.debug("Added the URL of the plugin {} to the thread class loader", file)

            val jar = JarFile(file)

            val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
                ?: return LOG.error("The plugin $jar does not have a main class")

            val loadClass = threadClassLoader.loadClass(mainClass)

            val loadInstance =
                loadClass.declaredFields.firstOrNull { it.name == "INSTANCE" }?.get(null)
                    ?: loadClass.constructors.firstOrNull()?.newInstance()
                        ?: return LOG.error("The plugin $jar does not have an object instance or a public constructor")

            val loadMethod =
                loadClass?.methods?.find { it.name == "load" }
                    ?: return LOG.warn("The plugin $jar does not have a load method")

            loadMethod.invoke(loadInstance)
        }.onFailure { LOG.error(errorMessageFMT, file, Thread.currentThread().stackTrace.joinToString("\n")) }
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
