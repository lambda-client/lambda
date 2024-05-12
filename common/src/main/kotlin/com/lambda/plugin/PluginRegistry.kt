package com.lambda.plugin

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.plugin.api.Plugin
import com.lambda.util.Communication.warn
import com.lambda.util.FolderRegister.createIfNotExists
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.plugins
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

object PluginRegistry : Loadable {
    private fun loadPlugin(file: File) {
        val loader = URLClassLoader(arrayOf(file.toURI().toURL()))
        val manifest = JarFile(file).manifest
        val mainClass = manifest.mainAttributes.getValue("Main-Class")

        val clazz = loader.loadClass(mainClass)
        val isObject = clazz.declaredFields.any { it.name == "INSTANCE" }
        val instance = if (isObject) clazz.getDeclaredField("INSTANCE").get(null)
        else clazz.getDeclaredConstructor().newInstance()

        if (instance is Plugin) instance.load()
        else LOG.warn("Plugin $file is not a valid plugin")
    }

    override fun load(): String {
        plugins.createIfNotExists()

        val plugins = plugins.listRecursive()
        plugins.forEach { file ->
            loadPlugin(file)
        }

        return "Loaded ${plugins.count()} plugins"
    }
}
