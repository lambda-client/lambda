package com.lambda.plugin

import com.lambda.Lambda.LOG
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister.createIfNotExists
import com.lambda.util.FolderRegister.listRecursive
import com.lambda.util.FolderRegister.plugins
import java.io.File
import java.util.jar.JarFile

object PluginRegistry : Loadable {
    private fun loadPlugin(file: File) {
        if (!file.name.endsWith(".jar")) return
        if (file.length() == 0L) return LOG.error("The plugin $file is empty")

        val jar = JarFile(file)
        val loader = PluginClassLoader(jar, this::class.java.classLoader)
        val mainClass = jar.manifest.mainAttributes.getValue("Main-Class")
            ?: return LOG.error("The plugin $jar does not have a main class")

        val clazz = loader.loadClass(mainClass)
        val instance =
            clazz.declaredFields.firstOrNull { it.name == "INSTANCE" }?.get(null) ?: clazz.constructors.firstOrNull()
                ?.newInstance()
            ?: return LOG.error("The plugin $jar does not have an object instance or a public constructor")

        val loadMethod =
            clazz.methods.find { it.name == "load" } ?: return LOG.warn("The plugin $jar does not have a load method")

        loadMethod.invoke(instance)
        loader.close()
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
