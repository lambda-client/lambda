package com.lambda.plugin

import com.lambda.Lambda.LOG
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL
import java.util.jar.JarFile

object PluginRegistry {
    private val knotClassLoader: ClassLoader = Thread.currentThread().contextClassLoader
    private val addUrlMethod: Method

    private var loadClass: Class<*>? = null
    private var loadMethod: Method? = null
    private var loadInstance: Any? = null // TODO: This won't work with constructors

    init {
        try {
            addUrlMethod = knotClassLoader.javaClass.getMethod("addUrlFwd", URL::class.java)
            addUrlMethod.isAccessible = true
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to get the addURL method from the KnotClassLoader.")
        }
    }

    @Throws(
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class)
    fun feedJarToKnot(jar: File) {
        addUrlMethod.invoke(knotClassLoader, jar.toURI().toURL())
    }

    private fun preLoadPlugin(file: File): PluginClassLoader? {
        runCatching { feedJarToKnot(file) }
            .onFailure {
                LOG.error("Failed to add the URL of the plugin $file", it)
                return null
            }

        LOG.debug("Added the URL of the plugin {} to the Knot class loader", file)

        val jar = JarFile(file)
        val loader = PluginClassLoader(jar, knotClassLoader)

        val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
            ?: return null.also { LOG.error("The plugin $jar does not have a main class") }

        loadClass = loader.loadClass(mainClass)

        loadInstance =
            loadClass?.declaredFields?.firstOrNull { it.name == "INSTANCE" }?.get(null)
                ?: loadClass?.constructors?.firstOrNull()?.newInstance()
                        ?: return null.also { LOG.error("The plugin $jar does not have an object instance or a public constructor") }

        loadMethod =
            loadClass?.methods?.find { it.name == "load" } ?: return null
                .also { LOG.warn("The plugin $jar does not have a load method") }

        return loader
    }

    private fun loadPlugin(file: File) {
        runCatching {
            loadMethod?.invoke(loadInstance)
        }
            .onFailure {
                return LOG.error("""
                A serious error occurred while loading a plugin.
                This is likely a bug in the plugin itself but it could also be a bug in Lambda.
                If you are a developer, please check the plugin's main class and load method.
                If you are a regular user, please report this issue to Lambda team and the plugin developer.
                
                Plugin: $file
                Stacktrace: ${Thread.currentThread().stackTrace.joinToString("\n")}
                """.trimIndent())
            }
    }

    fun preLoad(path: File): List<PluginClassLoader> {
        val plugins = path.walk().filter { it.isFile && it.extension == "jar" }.toList()
        return plugins.mapNotNull { preLoadPlugin(it) }
    }

    fun load(path: File): String {
        val plugins = path.walk().filter { it.isFile && it.extension == "jar" }.toList()
        plugins.forEach { loadPlugin(it) }
        return "Loaded ${plugins.size} plugins"
    }
}
